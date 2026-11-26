package com.hc.aiagenthyc.demo.invoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test of the JSON request body built by the {@link HttpAiInvoke} demo. Only the body builder is exercised;
 * nothing is sent to DashScope and no API key is needed.
 */
class HttpAiInvokeTests {

    @Test
    void preservesQuotesBackslashesUnicodeAndAllJsonControlCharacters() throws Exception {
        StringBuilder message = new StringBuilder("Hello \"quoted\" \\ path");
        // Every control character U+0000 to U+001F must be escaped for the body to stay valid JSON.
        for (char character = 0; character < 32; character++) {
            message.append(character);
        }

        JsonNode request = new ObjectMapper().readTree(HttpAiInvoke.buildRequestBody(message.toString()));

        assertEquals("qwen-plus", request.get("model").asText());
        // messages[0] is the fixed system prompt; messages[1] is the user message.
        assertEquals(message.toString(), request.get("messages").get(1).get("content").asText());
    }

    @Test
    void preservesNullAsEmptyMessage() throws Exception {
        JsonNode request = new ObjectMapper().readTree(HttpAiInvoke.buildRequestBody(null));

        assertEquals("", request.get("messages").get(1).get("content").asText());
    }
}
