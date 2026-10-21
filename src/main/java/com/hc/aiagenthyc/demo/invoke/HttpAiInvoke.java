package com.hc.aiagenthyc.demo.invoke;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Calls DashScope language models directly over HTTP.
 * Uses the OpenAI-compatible endpoint. Documentation:
 * https://help.aliyun.com/zh/model-studio/developer-reference/compatibility-of-openai-with-dashscope
 */
public class HttpAiInvoke {

    /** Beijing OpenAI-compatible endpoint; for Singapore use https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions */
    private static final String API_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private static final String MODEL = "qwen-plus";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static String callWithMessage(String userMessage) throws Exception {
        String requestBody = buildRequestBody(userMessage);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + TestApiKey.API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "DashScope HTTP call failed, status=" + response.statusCode() + ", body=" + response.body());
        }
        return response.body();
    }

    static String buildRequestBody(String userMessage) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(Map.of(
                "model", MODEL,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a helpful assistant."),
                        Map.of("role", "user", "content", userMessage == null ? "" : userMessage))));
    }

    public static void main(String[] args) {
        try {
            String result = callWithMessage("Who are you?");
            System.out.println(result);
        } catch (Exception e) {
            System.err.println("An error occurred while calling the generation service: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
