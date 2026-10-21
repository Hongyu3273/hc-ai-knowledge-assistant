package com.hc.aiagenthyc.app.prompt;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Loads and renders the classpath prompt templates used by LoveApp.
 */
@Component
public final class LovePromptTemplates {

    private static final String DEFAULT_LANGUAGE = "English";

    private static final String DEFAULT_RESPONSE_STYLE = "professional, warm, and specific";

    private static final String DEFAULT_USER_NAME = "User";

    private static final String DEFAULT_RELATIONSHIP_STATUS = "Not specified";

    private final String systemTemplate;

    private final String reportInstructionTemplate;

    public LovePromptTemplates(
            @Value("classpath:/prompts/love/system.st") Resource systemTemplateResource,
            @Value("classpath:/prompts/love/report-instruction.st") Resource reportInstructionTemplateResource) {
        this.systemTemplate = readTemplate(systemTemplateResource);
        this.reportInstructionTemplate = readTemplate(reportInstructionTemplateResource);
    }

    public String renderSystemPrompt() {
        return renderSystemPrompt(DEFAULT_LANGUAGE, DEFAULT_RESPONSE_STYLE);
    }

    public String renderSystemPrompt(String language, String responseStyle) {
        Assert.hasText(language, "language cannot be empty");
        Assert.hasText(responseStyle, "responseStyle cannot be empty");
        return new PromptTemplate(this.systemTemplate).render(Map.of(
                "language", language,
                "responseStyle", responseStyle));
    }

    public String renderReportInstruction(String question) {
        return renderReportInstruction(DEFAULT_USER_NAME, DEFAULT_RELATIONSHIP_STATUS, question);
    }

    public String renderReportInstruction(String userName, String relationshipStatus, String question) {
        Assert.hasText(question, "question cannot be empty");
        return new PromptTemplate(this.reportInstructionTemplate).render(Map.of(
                "userName", defaultIfBlank(userName, DEFAULT_USER_NAME),
                "relationshipStatus", defaultIfBlank(relationshipStatus, DEFAULT_RELATIONSHIP_STATUS),
                "question", question));
    }

    private static String readTemplate(Resource resource) {
        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            Assert.hasText(content, "Prompt template cannot be empty: " + resource.getDescription());
            return content;
        }
        catch (IOException exception) {
            throw new IllegalStateException("Failed to read prompt template: " + resource.getDescription(),
                    exception);
        }
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
