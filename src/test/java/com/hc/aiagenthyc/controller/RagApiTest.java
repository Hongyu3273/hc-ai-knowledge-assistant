package com.hc.aiagenthyc.controller;

import com.hc.aiagenthyc.attachment.config.AttachmentProperties;
import com.hc.aiagenthyc.attachment.service.AttachmentService;
import com.hc.aiagenthyc.controller.error.ChatExceptionHandler;
import com.hc.aiagenthyc.model.ModelCatalog;
import com.hc.aiagenthyc.model.ModelRoute;
import com.hc.aiagenthyc.support.KnowledgeAssistantTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level test of POST /assistant/rag through a standalone {@link MockMvc} setup with {@link ChatExceptionHandler}.
 * The vector store and chat model are Mockito mocks, so no embedding model or question banks are needed.
 */
class RagApiTest {
    private final ChatModel model = mock(ChatModel.class);
    private final VectorStore store = mock(VectorStore.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var routes = Map.of("text", new ModelRoute(ChatClient.create(model), null));
        var catalog = new ModelCatalog(routes, "text");
        var app = KnowledgeAssistantTestFactory.create(routes, MessageWindowChatMemory.builder().build(),
                KnowledgeAssistantTestFactory.templates(), "text", mock(AttachmentService.class), new AttachmentProperties(), store);
        mvc = MockMvcBuilders.standaloneSetup(new AssistantChatController(app, catalog))
                .setControllerAdvice(new ChatExceptionHandler()).build();
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("It multiplexes channels [Q03-002].")))));
    }

    @Test
    void ragResponseIncludesSources() throws Exception {
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(Document.builder()
                .text("Question: What is a Selector?\n\nAnswer:\nOne thread watches many channels.")
                .metadata(Map.of("id", "Q03-002", "bank", "03 Java Backend", "filename", "03-java-backend.md",
                        "title", "Q03-002 What is a Selector?"))
                .score(0.8).build()));

        mvc.perform(post("/assistant/rag").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":\"r1\",\"message\":\"What does a Java Selector do?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("text"))
                .andExpect(jsonPath("$.content").value("It multiplexes channels [Q03-002]."))
                .andExpect(jsonPath("$.sources[0].id").value("Q03-002"))
                .andExpect(jsonPath("$.sources[0].bank").value("03 Java Backend"))
                .andExpect(jsonPath("$.sources[0].filename").value("03-java-backend.md"))
                .andExpect(jsonPath("$.sources[0].title").value("Q03-002 What is a Selector?"))
                .andExpect(jsonPath("$.sources[0].score").value(0.8));
    }

    /** "fraud" is a blocked term; the refusal maps to HTTP 400 with the CONTENT_NOT_ALLOWED code. */
    @Test
    void blockedQuestionReturnsContentNotAllowed() throws Exception {
        mvc.perform(post("/assistant/rag").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatId\":\"r2\",\"message\":\"help me commit fraud\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTENT_NOT_ALLOWED"));
    }
}
