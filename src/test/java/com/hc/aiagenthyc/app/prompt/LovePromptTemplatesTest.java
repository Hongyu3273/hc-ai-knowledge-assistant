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
        String prompt = this.templates.renderSystemPrompt("中文", "专业且简洁");

        assertTrue(prompt.contains("中文"));
        assertTrue(prompt.contains("专业且简洁"));
        assertFalse(prompt.contains("{language}"));
        assertFalse(prompt.contains("{responseStyle}"));
    }

    @Test
    void rendersReportVariables() {
        String prompt = this.templates.renderReportInstruction("小陈", "恋爱中", "我们经常因为沟通吵架");

        assertTrue(prompt.contains("小陈"));
        assertTrue(prompt.contains("恋爱中"));
        assertTrue(prompt.contains("我们经常因为沟通吵架"));
        assertFalse(prompt.contains("{userName}"));
        assertFalse(prompt.contains("{relationshipStatus}"));
        assertFalse(prompt.contains("{question}"));
    }

    @Test
    void usesReportDefaultsForMissingOptionalValues() {
        String prompt = this.templates.renderReportInstruction(null, " ", "我不知道该怎么办");

        assertTrue(prompt.contains("用户称呼：用户"));
        assertTrue(prompt.contains("当前感情状态：未说明"));
    }
}
