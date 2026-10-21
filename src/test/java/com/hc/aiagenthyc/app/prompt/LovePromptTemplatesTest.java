package com.hc.aiagenthyc.app.prompt;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LovePromptTemplatesTest {

    private final LovePromptTemplates templates = new LovePromptTemplates(
            new ClassPathResource("prompts/love/system.st"),
            new ClassPathResource("prompts/love/report-instruction.st"));

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
        String prompt = this.templates.renderReportInstruction("Chen", "Dating", "We often argue about communication");

        assertTrue(prompt.contains("Chen"));
        assertTrue(prompt.contains("Dating"));
        assertTrue(prompt.contains("We often argue about communication"));
        assertFalse(prompt.contains("{userName}"));
        assertFalse(prompt.contains("{relationshipStatus}"));
        assertFalse(prompt.contains("{question}"));
    }

    @Test
    void usesReportDefaultsForMissingOptionalValues() {
        String prompt = this.templates.renderReportInstruction(null, " ", "I do not know what to do");

        assertTrue(prompt.contains("User name: User"));
        assertTrue(prompt.contains("Current relationship status: Not specified"));
    }
}
