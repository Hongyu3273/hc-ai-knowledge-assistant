package com.hc.aiagenthyc.controller;

import com.hc.aiagenthyc.app.KnowledgeAssistant;
import com.hc.aiagenthyc.model.ModelCatalog;
import com.hc.aiagenthyc.rag.RagAnswer;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cloud knowledge-base chat. Registered only when {@code app.assistant.rag.cloud.enabled=true}. */
@RestController
@RequestMapping("/assistant")
@ConditionalOnProperty(prefix = "app.assistant.rag.cloud", name = "enabled", havingValue = "true")
public class AssistantRagCloudController {
    private final KnowledgeAssistant app;
    private final ModelCatalog catalog;

    public AssistantRagCloudController(KnowledgeAssistant app, ModelCatalog catalog) {
        this.app = app;
        this.catalog = catalog;
    }

    /**
     * {@code POST /api/assistant/rag/cloud}: answers a text question with the default model, using passages retrieved
     * from the DashScope cloud knowledge base, and returns those passages as sources.
     */
    @Operation(summary = "Answer from the DashScope cloud knowledge base")
    @PostMapping("/rag/cloud")
    public AssistantChatController.RagReply ragCloud(@Valid @RequestBody AssistantChatController.RagRequest request) {
        RagAnswer answer = app.doChatWithRagCloud(request.message(), request.chatId());
        return new AssistantChatController.RagReply(request.chatId(), catalog.resolve(null), answer.content(), answer.sources());
    }
}
