package com.hc.aiagenthyc.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.ai.dashscope.api-key=test-key",
        "langchain4j.community.dashscope.chat-model.api-key=test-key",
        "app.gemini.enabled=false", "app.love.model=dashscope",
        "spring.servlet.multipart.max-file-size=100B", "spring.servlet.multipart.max-request-size=2KB"
})
class MultipartLimitIntegrationTest {
    @Autowired TestRestTemplate http;
    @Test void servletRejectsOversizedUploadWithStructuredError() {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ByteArrayResource(new byte[101]) {
            @Override public String getFilename() { return "oversized.png"; }
        });
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        var response = http.postForEntity("/attachments", new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().path("code").asText()).isEqualTo("ATTACHMENT_TOO_LARGE");
    }
    @Test void applicationExposesOnlyEnabledModels() {
        var response = http.getForEntity("/ai/models", JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("defaultModel").asText()).isEqualTo("dashscope");
        assertThat(response.getBody().path("models").size()).isEqualTo(1);
    }
}
