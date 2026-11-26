package com.hc.aiagenthyc.app.prompt;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test of {@link AssistantPromptTemplates} against the production templates in
 * {@code src/main/resources/prompts/assistant/}: variables are substituted and no placeholders are left behind.
 */
class AssistantPromptTemplatesTest {

    private final AssistantPromptTemplates templates = new AssistantPromptTemplates(
            new ClassPathResource("prompts/assistant/system.st"),
            new ClassPathResource("prompts/assistant/report-instruction.st"));

    @Test
    void rendersSystemPromptVariables() {
        String prompt = this.templates.renderSystemPrompt("English", "professional and concise");

        assertTrue(prompt.contains("English"));
        assertTrue(prompt.contains("professional and concise"));
        assertFalse(prompt.contains("{language}"));
        assertFalse(prompt.contains("{responseStyle}"));
    }

    @Test
    void rendersReportVariables() {
        String prompt = this.templates.renderReportInstruction("Chen", "Java backend", "I keep mixing up Spring bean scopes");

        assertTrue(prompt.contains("Chen"));
        assertTrue(prompt.contains("Java backend"));
        assertTrue(prompt.contains("I keep mixing up Spring bean scopes"));
        assertFalse(prompt.contains("{userName}"));
        assertFalse(prompt.contains("{focusArea}"));
        assertFalse(prompt.contains("{question}"));
    }

    /** A null or blank user name and focus area fall back to "User" and "Not specified". */
    @Test
    void usesReportDefaultsForMissingOptionalValues() {
        String prompt = this.templates.renderReportInstruction(null, " ", "I do not know what to do");

        // These labels come from report-instruction.st, so a template wording change also breaks this test.
        assertTrue(prompt.contains("User name: User"));
        assertTrue(prompt.contains("Focus area: Not specified"));
    }
}
