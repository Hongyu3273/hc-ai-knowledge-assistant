package com.hc.aiagenthyc.support;

import com.hc.aiagenthyc.app.LoveApp;
import com.hc.aiagenthyc.model.ModelRoute;
import com.hc.aiagenthyc.app.prompt.LovePromptTemplates;
import com.hc.aiagenthyc.app.service.ChatRequestValidator;
import com.hc.aiagenthyc.app.service.MultimodalMessageAssembler;
import com.hc.aiagenthyc.attachment.config.AttachmentProperties;
import com.hc.aiagenthyc.attachment.service.AttachmentService;
import com.hc.aiagenthyc.memory.ConversationExecutor;
import com.hc.aiagenthyc.model.ModelCatalog;
import jakarta.validation.Validation;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.core.io.ClassPathResource;
import java.util.Map;
import static org.mockito.Mockito.mock;

public final class LoveAppTestFactory {
    private static final jakarta.validation.ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private LoveAppTestFactory() { }
    public static LovePromptTemplates templates() {
        return new LovePromptTemplates(new ClassPathResource("prompts/love/system.st"),
                new ClassPathResource("prompts/love/report-instruction.st"));
    }
    public static LoveApp create(Map<String, ModelRoute> routes, ChatMemory memory,
                                 LovePromptTemplates templates, String defaultModel) {
        return create(routes, memory, templates, defaultModel, mock(AttachmentService.class), new AttachmentProperties());
    }
    public static LoveApp create(Map<String, ModelRoute> routes, ChatMemory memory,
                                 LovePromptTemplates templates, String defaultModel,
                                 AttachmentService attachments, AttachmentProperties properties) {
        return new LoveApp(new ModelCatalog(routes, defaultModel), memory, templates,
                new ChatRequestValidator(VALIDATORS.getValidator(), properties),
                new MultimodalMessageAssembler(attachments, properties), new ConversationExecutor(), new com.fasterxml.jackson.databind.ObjectMapper());
    }
}
