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
 * Loads and renders the classpath prompt templates used by KnowledgeAssistant.
 *
 * <p>Both templates ({@code prompts/assistant/system.st} and {@code prompts/assistant/report-instruction.st}) are
 * read once at construction, so a missing or empty template fails application startup.</p>
 */
@Component
public final class AssistantPromptTemplates {

    private static final String DEFAULT_LANGUAGE = "English";

    private static final String DEFAULT_RESPONSE_STYLE = "clear, precise, and professional";

    private static final String DEFAULT_USER_NAME = "User";

    private static final String DEFAULT_FOCUS_AREA = "Not specified";

    private final String systemTemplate;

    private final String reportInstructionTemplate;

    /**
     * @throws IllegalStateException    if a template cannot be read
     * @throws IllegalArgumentException if a template is empty
     */
    public AssistantPromptTemplates(
            @Value("classpath:/prompts/assistant/system.st") Resource systemTemplateResource,
            @Value("classpath:/prompts/assistant/report-instruction.st") Resource reportInstructionTemplateResource) {
        this.systemTemplate = readTemplate(systemTemplateResource);
        this.reportInstructionTemplate = readTemplate(reportInstructionTemplateResource);
    }

    /** Renders the system prompt with the default language (English) and response style. */
    public String renderSystemPrompt() {
        return renderSystemPrompt(DEFAULT_LANGUAGE, DEFAULT_RESPONSE_STYLE);
    }

    /**
     * Renders the system prompt.
     *
     * @param language      answer language; must not be blank
     * @param responseStyle short description of the desired tone; must not be blank
     * @throws IllegalArgumentException if either argument is blank
     */
    public String renderSystemPrompt(String language, String responseStyle) {
        Assert.hasText(language, "language cannot be empty");
        Assert.hasText(responseStyle, "responseStyle cannot be empty");
        return new PromptTemplate(this.systemTemplate).render(Map.of(
                "language", language,
                "responseStyle", responseStyle));
    }

    /** Renders the report instruction with the default user name and focus area. */
    public String renderReportInstruction(String question) {
        return renderReportInstruction(DEFAULT_USER_NAME, DEFAULT_FOCUS_AREA, question);
    }

    /**
     * Wraps the user's request in the study-report instruction.
     *
     * @param userName  name shown in the prompt; blank falls back to {@code User}
     * @param focusArea topic to focus on; blank falls back to {@code Not specified}
     * @param question  the user's request; must not be blank
     * @throws IllegalArgumentException if {@code question} is blank
     */
    public String renderReportInstruction(String userName, String focusArea, String question) {
        Assert.hasText(question, "question cannot be empty");
        return new PromptTemplate(this.reportInstructionTemplate).render(Map.of(
                "userName", defaultIfBlank(userName, DEFAULT_USER_NAME),
                "focusArea", defaultIfBlank(focusArea, DEFAULT_FOCUS_AREA),
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
