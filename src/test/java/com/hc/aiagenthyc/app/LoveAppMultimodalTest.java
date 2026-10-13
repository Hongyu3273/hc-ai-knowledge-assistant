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
import com.hc.aiagenthyc.support.LoveAppTestFactory;
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

class LoveAppMultimodalTest {
    @TempDir Path directory;
    private final ChatModel vision = mock(ChatModel.class);
    private final ChatModel text = mock(ChatModel.class);
    private Map<String, ModelRoute> routes;
    private AttachmentProperties properties;
    private AttachmentService attachments;
    private FileBasedChatMemoryRepository repository;
    private String fileId;
    private byte[] image;
    private LoveApp app;

    @BeforeEach void setUp() throws Exception {
        properties = new AttachmentProperties();
        properties.setDirectory(directory.resolve("attachments").toString());
        attachments = new AttachmentService(new LocalAttachmentStorage(properties, new ObjectMapper()), properties);
        image = image("png", 2, 2);
        fileId = attachments.upload(new MockMultipartFile("file", image)).fileId();
        var client = ChatClient.create(vision);
        routes = Map.of("pro", new ModelRoute(client, "gemini-2.5-pro", Set.of(InputType.TEXT, InputType.IMAGE)),
                "lite", new ModelRoute(client, "gemini-2.5-flash-lite", Set.of(InputType.TEXT, InputType.IMAGE)),
                "text", new ModelRoute(ChatClient.create(text), null));
        when(vision.call(any(Prompt.class))).thenReturn(response("图片回答"));
        app = reopen(6);
    }
    private LoveApp reopen(int window) {
        repository = new FileBasedChatMemoryRepository(directory.resolve("memory").toString());
        var memory = MessageWindowChatMemory.builder().chatMemoryRepository(repository).maxMessages(window).build();
        return LoveAppTestFactory.create(routes, memory, LoveAppTestFactory.templates(), "pro", attachments, properties);
    }
    private LoveChatRequest request(String chatId, String model, String text, boolean image) {
        return new LoveChatRequest(chatId, model, text, image ? List.of(new ChatAttachment(fileId)) : List.of());
    }
    private LoveReportRequest report(String chatId, String model, String text, boolean image) {
        return new LoveReportRequest(chatId, model, text, image ? List.of(new ChatAttachment(fileId)) : List.of(), null, null);
    }
    @Test void persistsReferencesAndRehydratesHistoryAfterRestart() {
        app.doChat(request("a", null, "分析图片", true));
        var saved = repository.findByConversationId("a");
        assertThat(saved).hasSize(2);
        UserMessage user = (UserMessage) saved.getFirst();
        assertThat(user.getMedia()).isEmpty();
        assertThat(user.getMetadata().get(MultimodalMessageAssembler.ATTACHMENT_IDS)).isEqualTo(List.of(fileId));
        app = reopen(6);
        app.doChat(request("a", "lite", "最后一句呢", false));
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(vision, times(2)).call(prompts.capture());
        Prompt followup = prompts.getAllValues().getLast();
        assertThat(followup.getOptions().getModel()).isEqualTo("gemini-2.5-flash-lite");
        UserMessage original = followup.getInstructions().stream().filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast).findFirst().orElseThrow();
        assertThat(original.getMedia()).hasSize(1);
        assertThat((byte[]) original.getMedia().getFirst().getData()).isEqualTo(image);
        assertThat(repository.findByConversationId("a")).hasSize(4);
    }
    @Test void rejectsTextModelForCurrentAndHistoricalImages() {
        assertCode(() -> app.doChat(request("a", "text", "看看图片", true)), ErrorCode.MODEL_INPUT_NOT_SUPPORTED);
        assertThat(repository.findByConversationId("a")).isEmpty();
        app.doChat(request("a", "pro", "看看图片", true));
        assertCode(() -> app.doChat(request("a", "text", "继续", false)), ErrorCode.MODEL_INPUT_NOT_SUPPORTED);
        assertThat(repository.findByConversationId("a")).hasSize(2);
        verify(text, never()).call(any(Prompt.class));
    }
    @Test void imageOnlyUsesDefaultInstructionAndRejectsEmptyOrMalformedInput() {
        app.doChat(request("a", null, null, true));
        assertThat(repository.findByConversationId("a").getFirst().getText()).contains("描述并分析");
        assertCode(() -> app.doChat(request("b", null, " ", false)), ErrorCode.EMPTY_CHAT_INPUT);
        assertCode(() -> app.doChat(new LoveChatRequest("x", null, "hi", Arrays.asList((ChatAttachment) null))), ErrorCode.INVALID_REQUEST);
        assertCode(() -> app.doChat(new LoveChatRequest("中".repeat(43), null, "hi", null)), ErrorCode.INVALID_REQUEST);
        assertCode(() -> app.doChat(new LoveChatRequest("b", null, "hi", List.of(new ChatAttachment(fileId), new ChatAttachment(fileId)))), ErrorCode.INVALID_REQUEST);
    }
    @Test void failuresAndRefusalsDoNotCommitHistory() {
        when(vision.call(any(Prompt.class))).thenThrow(new RuntimeException("upstream secret"));
        assertCode(() -> app.doChat(request("a", null, "问题", true)), ErrorCode.MODEL_CALL_FAILED);
        assertThat(repository.findByConversationId("a")).isEmpty();
        assertCode(() -> app.doChat(request("b", null, "测试违禁词", true)), ErrorCode.CONTENT_NOT_ALLOWED);
        assertThat(repository.findByConversationId("b")).isEmpty();
    }
    @Test void reportIncludesMediaAndOnlyCommitsValidOutput() {
        when(vision.call(any(Prompt.class))).thenReturn(response("{\"title\":\"沟通建议\",\"suggestions\":[\"认真倾听\"]}"));
        assertThat(app.doChatWithReport(report("a", "lite", "生成报告", true)).title()).isEqualTo("沟通建议");
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(vision).call(prompt.capture());
        assertThat(prompt.getValue().getUserMessage().getMedia()).hasSize(1);
        assertThat(repository.findByConversationId("a")).hasSize(2);
        when(vision.call(any(Prompt.class))).thenReturn(response("not json"));
        assertCode(() -> app.doChatWithReport(report("b", null, "生成报告", true)), ErrorCode.MODEL_CALL_FAILED);
        assertThat(repository.findByConversationId("b")).isEmpty();
    }
    @Test void missingAttachmentAndContextLimitFailBeforeCallingModel() throws Exception {
        properties.setMaxContextBytes(1);
        assertCode(() -> app.doChat(request("a", null, "图片", true)), ErrorCode.ATTACHMENT_TOO_LARGE);
        properties.setMaxContextBytes(10000);
        Files.delete(Path.of(properties.getDirectory()).resolve(fileId).resolve("content"));
        assertCode(() -> app.doChat(request("a", null, "图片", true)), ErrorCode.ATTACHMENT_UNAVAILABLE);
        verify(vision, never()).call(any(Prompt.class));
    }
    @Test void evictedImageNoLongerBlocksTextModel() {
        app = reopen(2);
        app.doChat(request("a", "pro", "图片", true));
        app.doChat(request("a", "pro", "继续", false));
        when(text.call(any(Prompt.class))).thenReturn(response("文字回答"));
        assertThat(app.doChat(request("a", "text", "你好", false))).isEqualTo("文字回答");
    }
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
