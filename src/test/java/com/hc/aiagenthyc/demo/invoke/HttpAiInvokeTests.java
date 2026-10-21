package com.hc.aiagenthyc.demo.invoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpAiInvokeTests {

    @Test
    void preservesQuotesBackslashesUnicodeAndAllJsonControlCharacters() throws Exception {
        StringBuilder message = new StringBuilder("Hello \"quoted\" \\ path");
        for (char character = 0; character < 32; character++) {
            message.append(character);
        }

        JsonNode request = new ObjectMapper().readTree(HttpAiInvoke.buildRequestBody(message.toString()));

        assertEquals("qwen-plus", request.get("model").asText());
        assertEquals(message.toString(), request.get("messages").get(1).get("content").asText());
    }

    @Test
    void preservesNullAsEmptyMessage() throws Exception {
        JsonNode request = new ObjectMapper().readTree(HttpAiInvoke.buildRequestBody(null));

        assertEquals("", request.get("messages").get(1).get("content").asText());
    }
}
