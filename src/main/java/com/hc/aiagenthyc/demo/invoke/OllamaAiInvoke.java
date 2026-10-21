package com.hc.aiagenthyc.demo.invoke;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Calls a local Qwen model through Ollama.
 * Defaults to http://127.0.0.1:11434 and the qwen3-14b-local:latest model.
 * Override the defaults with OLLAMA_BASE_URL and OLLAMA_MODEL environment variables.
 */
public final class OllamaAiInvoke {

    private static final String BASE_URL =
            System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://127.0.0.1:11434");
    private static final String MODEL =
            System.getenv().getOrDefault("OLLAMA_MODEL", "qwen3-14b-local:latest");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private OllamaAiInvoke() {
    }

    public static String callWithMessage(String userMessage) throws Exception {
        String requestBody = OBJECT_MAPPER.writeValueAsString(Map.of(
                "model", MODEL,
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a knowledgeable AI chat assistant. Answer the user's questions."),
                        Map.of("role", "user", "content", userMessage == null ? "" : userMessage))));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl(BASE_URL) + "/api/chat"))
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response =
                HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Ollama call failed, status=" + response.statusCode() + ", body=" + response.body());
        }

        JsonNode content = OBJECT_MAPPER.readTree(response.body()).path("message").path("content");
        if (content.isMissingNode() || content.isNull()) {
            throw new IllegalStateException("The Ollama response is missing message.content: " + response.body());
        }
        return content.asText();
    }

    private static String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public static void main(String[] args) {
        try {
            String message = args.length > 0 ? String.join(" ", args) : "Who are you?";
            System.out.println(callWithMessage(message));
        } catch (Exception e) {
            System.err.println("Local Ollama model call failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
