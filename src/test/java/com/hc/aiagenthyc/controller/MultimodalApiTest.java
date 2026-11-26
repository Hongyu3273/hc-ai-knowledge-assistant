package com.hc.aiagenthyc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hc.aiagenthyc.model.ModelRoute;
import com.hc.aiagenthyc.attachment.config.AttachmentProperties;
import com.hc.aiagenthyc.attachment.service.AttachmentService;
import com.hc.aiagenthyc.attachment.storage.LocalAttachmentStorage;
import com.hc.aiagenthyc.controller.error.ChatExceptionHandler;
import com.hc.aiagenthyc.model.*;
import com.hc.aiagenthyc.support.KnowledgeAssistantTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.Path;
import java.util.*;
import static com.hc.aiagenthyc.support.ImageFixtures.image;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP-level test of the chat, report, attachment and model-catalog endpoints through a standalone {@link MockMvc}
 * setup with {@link ChatExceptionHandler}, without a Spring context. Attachments are stored in a temporary directory
 * and the chat model is a Mockito mock. Paths omit the /api servlet context path.
 */
class MultimodalApiTest {
    @TempDir Path directory;
    private MockMvc mvc;
    private final ChatModel model = mock(ChatModel.class);
    @BeforeEach void setUp() {
        var properties = new AttachmentProperties();
        properties.setDirectory(directory.toString());
        var attachments = new AttachmentService(new LocalAttachmentStorage(properties, new ObjectMapper()), properties);
        var client = ChatClient.create(model);
        var routes = Map.of("pro", new ModelRoute(client, "gemini-2.5-pro", Set.of(InputType.TEXT, InputType.IMAGE)),
                "text", new ModelRoute(client, null));
        // The controllers and the assistant each get a catalog over the same routes and default.
        var catalog = new ModelCatalog(routes, "pro");
        var app = KnowledgeAssistantTestFactory.create(routes, MessageWindowChatMemory.builder().build(),
                KnowledgeAssistantTestFactory.templates(), "pro", attachments, properties);
        mvc = MockMvcBuilders.standaloneSetup(new AssistantChatController(app, catalog),
                        new AttachmentController(attachments), new ModelController(catalog))
                .setControllerAdvice(new ChatExceptionHandler()).build();
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Response")))));
    }
    @Test void uploadChatFollowupAndCapabilityDiscovery() throws Exception {
        var response = mvc.perform(multipart("/attachments").file(new MockMultipartFile("file", "sample.png", "image/png", image("png", 2, 2))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.mimeType").value("image/png"))
                // The response must not reveal where or how the file is stored.
                .andExpect(jsonPath("$.storageKey").doesNotExist()).andReturn().getResponse().getContentAsString();
        String id = new ObjectMapper().readTree(response).get("fileId").asText();
        String body = "{\"chatId\":\"a\",\"text\":\"Look at the image\",\"attachments\":[{\"fileId\":\"" + id + "\"}]}";
        mvc.perform(post("/assistant/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.model").value("pro"))
                .andExpect(jsonPath("$.content").value("Response"));
        mvc.perform(post("/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":\"a\",\"model\":\"text\",\"text\":\"Continue\"}"))
                // Chat "a" now holds an image, which the text-only route cannot accept even for a text message.
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MODEL_INPUT_NOT_SUPPORTED"));
        mvc.perform(get("/ai/models")).andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultModel").value("pro"))
                // Models are sorted by ID, so models[0] is "pro", the image-capable route.
                .andExpect(jsonPath("$.models[0].supportedInputs").value(org.hamcrest.Matchers.hasItem("image")));
    }
    @Test void reportReturnsStructuredResponse() throws Exception {
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(
                new AssistantMessage("{\"title\":\"Communication advice\",\"suggestions\":[\"Listen more\"]}")))));
        mvc.perform(post("/assistant/report").contentType(MediaType.APPLICATION_JSON).content("{\"chatId\":\"a\",\"text\":\"Advice\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.report.title").value("Communication advice"));
    }
    @Test void validationAndProviderErrorsAreSanitized() throws Exception {
        // Passes bean validation; the assistant then rejects a message with neither text nor attachments.
        mvc.perform(post("/assistant/chat").contentType(MediaType.APPLICATION_JSON).content("{\"chatId\":\"a\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMPTY_CHAT_INPUT"));
        // A missing chatId fails @Valid on the request body.
        mvc.perform(post("/assistant/chat").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/assistant/chat").contentType(MediaType.APPLICATION_JSON).content("{\"chatId\":\"a\",\"text\":\"hi\",\"model\":\"unknown\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MODEL_NOT_AVAILABLE"));
        // The provider message stands in for a leaked secret; the API response must not echo it.
        when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("secret-api-key"));
        String response = mvc.perform(post("/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":\"a\",\"text\":\"hi\"}"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("MODEL_CALL_FAILED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("secret-api-key");
    }
    @Test void uploadRejectsInvalidContentAndMissingPart() throws Exception {
        mvc.perform(multipart("/attachments").file(new MockMultipartFile("file", "x.png", "image/png", new byte[]{1,2})))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSUPPORTED_ATTACHMENT_TYPE"));
        // No "file" part at all: Spring's missing-part error is mapped to INVALID_REQUEST.
        mvc.perform(multipart("/attachments"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
