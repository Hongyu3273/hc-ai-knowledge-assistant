package com.hc.aiagenthyc.support;

import com.hc.aiagenthyc.app.KnowledgeAssistant;
import com.hc.aiagenthyc.model.ModelRoute;
import com.hc.aiagenthyc.app.prompt.AssistantPromptTemplates;
import com.hc.aiagenthyc.app.service.ChatRequestValidator;
import com.hc.aiagenthyc.app.service.MultimodalMessageAssembler;
import com.hc.aiagenthyc.attachment.config.AttachmentProperties;
import com.hc.aiagenthyc.attachment.service.AttachmentService;
import com.hc.aiagenthyc.memory.ConversationExecutor;
import com.hc.aiagenthyc.model.ModelCatalog;
import jakarta.validation.Validation;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import com.hc.aiagenthyc.rag.RagProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import java.util.Optional;
import static org.mockito.Mockito.mock;

/**
 * Builds a {@link KnowledgeAssistant} without Spring, wiring the real validator, message assembler, conversation
 * executor and prompt templates around the routes, memory and stores a test supplies.
 *
 * <p>The overloads fill in defaults from the shortest to the longest: a mock {@link AttachmentService}, default
 * {@link AttachmentProperties}, a mock {@link VectorStore}, no cloud RAG advisor (cloud RAG disabled), default
 * {@link RagProperties} and a fresh {@link SimpleMeterRegistry}. The logging advisor is always off.</p>
 */
public final class KnowledgeAssistantTestFactory {
    private static final jakarta.validation.ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private KnowledgeAssistantTestFactory() { }
    /** Loads the production system and report prompt templates from the classpath. */
    public static AssistantPromptTemplates templates() {
        return new AssistantPromptTemplates(new ClassPathResource("prompts/assistant/system.st"),
                new ClassPathResource("prompts/assistant/report-instruction.st"));
    }
    /**
     * Creates an assistant for text-only tests, with a mock attachment service and default attachment limits.
     *
     * @param routes       routes keyed by model ID
     * @param memory       conversation memory
     * @param templates    prompt templates
     * @param defaultModel route ID used when a request names no model; must be a key of {@code routes}
     */
    public static KnowledgeAssistant create(Map<String, ModelRoute> routes, ChatMemory memory,
                                 AssistantPromptTemplates templates, String defaultModel) {
        return create(routes, memory, templates, defaultModel, mock(AttachmentService.class), new AttachmentProperties());
    }
    /** Creates an assistant with real attachment handling and a mock knowledge-base vector store. */
    public static KnowledgeAssistant create(Map<String, ModelRoute> routes, ChatMemory memory,
                                 AssistantPromptTemplates templates, String defaultModel,
                                 AttachmentService attachments, AttachmentProperties properties) {
        return create(routes, memory, templates, defaultModel, attachments, properties, mock(VectorStore.class));
    }
    /**
     * Creates an assistant with the given knowledge-base vector store and cloud RAG disabled, as in the default
     * configuration ({@code app.assistant.rag.cloud.enabled=false}). Tests of cloud RAG pass a retrieval advisor
     * through the longer overloads.
     */
    public static KnowledgeAssistant create(Map<String, ModelRoute> routes, ChatMemory memory,
                                 AssistantPromptTemplates templates, String defaultModel,
                                 AttachmentService attachments, AttachmentProperties properties, VectorStore vectorStore) {
        return create(routes, memory, templates, defaultModel, attachments, properties, vectorStore, null);
    }
    /**
     * Creates an assistant with the given cloud RAG advisor.
     *
     * @param cloudAdvisor advisor used by {@code doChatWithRagCloud}, or {@code null} to leave cloud RAG disabled
     */
    public static KnowledgeAssistant create(Map<String, ModelRoute> routes, ChatMemory memory,
                                 AssistantPromptTemplates templates, String defaultModel,
                                 AttachmentService attachments, AttachmentProperties properties, VectorStore vectorStore,
                                 org.springframework.ai.chat.client.advisor.api.Advisor cloudAdvisor) {
        return create(routes, memory, templates, defaultModel, attachments, properties, vectorStore, cloudAdvisor,
                new RagProperties(), new SimpleMeterRegistry());
    }
    /**
     * Creates an assistant with every collaborator supplied by the caller. Pass a {@link SimpleMeterRegistry} to
     * inspect the recorded meters, or a {@link RagProperties} instance to change retrieval settings during a test.
     *
     * @throws IllegalArgumentException if {@code defaultModel} is not a key of {@code routes}
     */
    public static KnowledgeAssistant create(Map<String, ModelRoute> routes, ChatMemory memory,
                                 AssistantPromptTemplates templates, String defaultModel,
                                 AttachmentService attachments, AttachmentProperties properties, VectorStore vectorStore,
                                 org.springframework.ai.chat.client.advisor.api.Advisor cloudAdvisor,
                                 RagProperties ragProperties, MeterRegistry meterRegistry) {
        // The final false keeps MyLoggerAdvisor off, as in the default configuration.
        return new KnowledgeAssistant(new ModelCatalog(routes, defaultModel), memory, templates,
                new ChatRequestValidator(VALIDATORS.getValidator(), properties),
                new MultimodalMessageAssembler(attachments, properties), new ConversationExecutor(),
                new com.fasterxml.jackson.databind.ObjectMapper(), vectorStore,
                Optional.ofNullable(cloudAdvisor), ragProperties, Optional.of(meterRegistry), false);
    }
}
