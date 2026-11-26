package com.hc.aiagenthyc.app;

import com.hc.aiagenthyc.model.ModelRoute;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hc.aiagenthyc.app.model.*;
import com.hc.aiagenthyc.app.service.MultimodalMessageAssembler;
import com.hc.aiagenthyc.attachment.config.AttachmentProperties;
import com.hc.aiagenthyc.attachment.service.AttachmentService;
import com.hc.aiagenthyc.attachment.storage.LocalAttachmentStorage;
import com.hc.aiagenthyc.common.error.ChatException;
import com.hc.aiagenthyc.common.error.ErrorCode;
import com.hc.aiagenthyc.memory.FileBasedChatMemoryRepository;
import com.hc.aiagenthyc.model.InputType;
import com.hc.aiagenthyc.support.KnowledgeAssistantTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static com.hc.aiagenthyc.support.ImageFixtures.image;

/**
 * Unit test of image handling in {@link KnowledgeAssistant}: attachment references in memory, rehydrating images from
 * history, input-type checks per route, request validation, and which turns are committed to memory.
 *
 * <p>Uses real attachment storage and a real {@link FileBasedChatMemoryRepository} in a temporary directory, so a
 * restart is simulated by building a new assistant over the same files. The chat models are Mockito mocks.</p>
 */
class KnowledgeAssistantMultimodalTest {
    @TempDir Path directory;
    private final ChatModel vision = mock(ChatModel.class);
    private final ChatModel text = mock(ChatModel.class);
    private Map<String, ModelRoute> routes;
    private AttachmentProperties properties;
    private AttachmentService attachments;
    private FileBasedChatMemoryRepository repository;
    private String fileId;
    private byte[] image;
    private KnowledgeAssistant app;

    @BeforeEach void setUp() throws Exception {
        properties = new AttachmentProperties();
        properties.setDirectory(directory.resolve("attachments").toString());
        attachments = new AttachmentService(new LocalAttachmentStorage(properties, new ObjectMapper()), properties);
        image = image("png", 2, 2);
        fileId = attachments.upload(new MockMultipartFile("file", image)).fileId();
        // "pro" and "lite" share one client, as the two Gemini routes do; only the per-request model differs.
        var client = ChatClient.create(vision);
        routes = Map.of("pro", new ModelRoute(client, "gemini-2.5-pro", Set.of(InputType.TEXT, InputType.IMAGE)),
                "lite", new ModelRoute(client, "gemini-2.5-flash-lite", Set.of(InputType.TEXT, InputType.IMAGE)),
                "text", new ModelRoute(ChatClient.create(text), null));
        when(vision.call(any(Prompt.class))).thenReturn(response("Image response"));
        app = reopen(6);
    }
    /** Builds a new assistant over the same memory files, as after a restart, with a window of {@code window}. */
    private KnowledgeAssistant reopen(int window) {
        repository = new FileBasedChatMemoryRepository(directory.resolve("memory").toString());
        var memory = MessageWindowChatMemory.builder().chatMemoryRepository(repository).maxMessages(window).build();
        return KnowledgeAssistantTestFactory.create(routes, memory, KnowledgeAssistantTestFactory.templates(), "pro", attachments, properties);
    }
    private AssistantChatRequest request(String chatId, String model, String text, boolean image) {
        return new AssistantChatRequest(chatId, model, text, image ? List.of(new ChatAttachment(fileId)) : List.of());
    }
    private StudyReportRequest report(String chatId, String model, String text, boolean image) {
        return new StudyReportRequest(chatId, model, text, image ? List.of(new ChatAttachment(fileId)) : List.of(), null, null);
    }
    @Test void persistsReferencesAndRehydratesHistoryAfterRestart() {
        app.doChat(request("a", null, "Analyze the image", true));
        var saved = repository.findByConversationId("a");
        assertThat(saved).hasSize(2);
        UserMessage user = (UserMessage) saved.getFirst();
        // Memory stores the attachment ID, not the image bytes.
        assertThat(user.getMedia()).isEmpty();
        assertThat(user.getMetadata().get(MultimodalMessageAssembler.ATTACHMENT_IDS)).isEqualTo(List.of(fileId));
        app = reopen(6);
        app.doChat(request("a", "lite", "What about the last sentence?", false));
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(vision, times(2)).call(prompts.capture());
        Prompt followup = prompts.getAllValues().getLast();
        assertThat(followup.getOptions().getModel()).isEqualTo("gemini-2.5-flash-lite");
        // The first user message is the earlier image turn, rebuilt from its stored ID for the new route.
        UserMessage original = followup.getInstructions().stream().filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast).findFirst().orElseThrow();
        assertThat(original.getMedia()).hasSize(1);
        assertThat((byte[]) original.getMedia().getFirst().getData()).isEqualTo(image);
        assertThat(repository.findByConversationId("a")).hasSize(4);
    }
    @Test void rejectsTextModelForCurrentAndHistoricalImages() {
        assertCode(() -> app.doChat(request("a", "text", "Look at the image", true)), ErrorCode.MODEL_INPUT_NOT_SUPPORTED);
        assertThat(repository.findByConversationId("a")).isEmpty();
        app.doChat(request("a", "pro", "Look at the image", true));
        // The new message is text only, but the history still holds an image the text route cannot accept.
        assertCode(() -> app.doChat(request("a", "text", "Continue", false)), ErrorCode.MODEL_INPUT_NOT_SUPPORTED);
        assertThat(repository.findByConversationId("a")).hasSize(2);
        verify(text, never()).call(any(Prompt.class));
    }
    @Test void imageOnlyUsesDefaultInstructionAndRejectsEmptyOrMalformedInput() {
        app.doChat(request("a", null, null, true));
        assertThat(repository.findByConversationId("a").getFirst().getText()).contains("describe and analyze");
        assertCode(() -> app.doChat(request("b", null, " ", false)), ErrorCode.EMPTY_CHAT_INPUT);
        assertCode(() -> app.doChat(new AssistantChatRequest("x", null, "hi", Arrays.asList((ChatAttachment) null))), ErrorCode.INVALID_REQUEST);
        // 43 characters pass @Size(max = 128), but each euro sign is 3 UTF-8 bytes: 129 bytes exceed the byte limit.
        assertCode(() -> app.doChat(new AssistantChatRequest("\u20AC".repeat(43), null, "hi", null)), ErrorCode.INVALID_REQUEST);
        // Only one image is allowed per message.
        assertCode(() -> app.doChat(new AssistantChatRequest("b", null, "hi", List.of(new ChatAttachment(fileId), new ChatAttachment(fileId)))), ErrorCode.INVALID_REQUEST);
    }
    @Test void failuresAndRefusalsDoNotCommitHistory() {
        // The provider error is replaced by a generic MODEL_CALL_FAILED so its message is not exposed.
        when(vision.call(any(Prompt.class))).thenThrow(new RuntimeException("upstream secret"));
        assertCode(() -> app.doChat(request("a", null, "Question", true)), ErrorCode.MODEL_CALL_FAILED);
        assertThat(repository.findByConversationId("a")).isEmpty();
        assertCode(() -> app.doChat(request("b", null, "test prohibited phrase", true)), ErrorCode.CONTENT_NOT_ALLOWED);
        assertThat(repository.findByConversationId("b")).isEmpty();
    }
    @Test void reportIncludesMediaAndOnlyCommitsValidOutput() {
        when(vision.call(any(Prompt.class))).thenReturn(response("{\"title\":\"Communication advice\",\"suggestions\":[\"Listen carefully\"]}"));
        assertThat(app.doChatWithReport(report("a", "lite", "Generate a report", true)).title()).isEqualTo("Communication advice");
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(vision).call(prompt.capture());
        assertThat(prompt.getValue().getUserMessage().getMedia()).hasSize(1);
        assertThat(repository.findByConversationId("a")).hasSize(2);
        // An unparseable report fails the turn, so nothing is saved for chat "b".
        when(vision.call(any(Prompt.class))).thenReturn(response("not json"));
        assertCode(() -> app.doChatWithReport(report("b", null, "Generate a report", true)), ErrorCode.MODEL_CALL_FAILED);
        assertThat(repository.findByConversationId("b")).isEmpty();
    }
    @Test void missingAttachmentAndContextLimitFailBeforeCallingModel() throws Exception {
        // The assembler reads the shared properties on each call, so changing them here takes effect immediately.
        properties.setMaxContextBytes(1);
        assertCode(() -> app.doChat(request("a", null, "Image", true)), ErrorCode.ATTACHMENT_TOO_LARGE);
        properties.setMaxContextBytes(10000);
        // Remove the stored bytes but keep the metadata, so the reference resolves to a broken attachment.
        Files.delete(Path.of(properties.getDirectory()).resolve(fileId).resolve("content"));
        assertCode(() -> app.doChat(request("a", null, "Image", true)), ErrorCode.ATTACHMENT_UNAVAILABLE);
        verify(vision, never()).call(any(Prompt.class));
    }
    /** With a two-message window the image turn drops out of memory, so the text-only route can take over. */
    @Test void evictedImageNoLongerBlocksTextModel() {
        app = reopen(2);
        app.doChat(request("a", "pro", "Image", true));
        app.doChat(request("a", "pro", "Continue", false));
        when(text.call(any(Prompt.class))).thenReturn(response("Text response"));
        assertThat(app.doChat(request("a", "text", "Hello", false))).isEqualTo("Text response");
    }
    /**
     * The first turn is held inside the model call while a second turn for the same chat is submitted. The
     * conversation lock makes the second turn wait, so it is saved after the first and sees its history.
     */
    @Test void concurrentTurnsOfSameConversationAreCommittedInOrder() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(vision.call(any(Prompt.class))).thenAnswer(invocation -> {
            if (((Prompt) invocation.getArgument(0)).getUserMessage().getText().equals("first")) {
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
            }
            return response("ok");
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> app.doChat("first", "a"));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> app.doChat("second", "a"));
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("ok");
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("ok");
        }
        assertThat(repository.findByConversationId("a")).extracting(Message::getText)
                .containsExactly("first", "ok", "second", "ok");
    }
    private static ChatResponse response(String text) { return new ChatResponse(List.of(new Generation(new AssistantMessage(text)))); }
    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation, ErrorCode code) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(ChatException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
