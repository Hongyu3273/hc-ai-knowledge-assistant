package com.hc.aiagenthyc.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.hc.aiagenthyc.config.GeminiConfiguration;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.hc.aiagenthyc.model.ModelRoute;
import com.hc.aiagenthyc.app.prompt.LovePromptTemplates;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.core.io.ClassPathResource;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.ArrayList;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the real Google SDK and Spring AI against a local HTTP stub without consuming API quota. */
class LoveAppGeminiIntegrationTest {
    private final LinkedBlockingQueue<CapturedRequest> requests = new LinkedBlockingQueue<>();
    private HttpServer server;
    private Client client;
    private LoveApp app;
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    private com.hc.aiagenthyc.attachment.service.AttachmentService attachments;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(new CapturedRequest(exchange.getRequestURI().getPath(),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                    exchange.getRequestHeaders().getFirst("x-goog-api-key")));
            byte[] response = """
                    {"modelVersion":"test-model","candidates":[{"content":{"role":"model","parts":[{"text":"Hello"}]},
                    "finishReason":"STOP","index":0}],
                    "usageMetadata":{"promptTokenCount":4,"candidatesTokenCount":2,"totalTokenCount":6}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        client = Client.builder().vertexAI(false).apiKey("test-key")
                .httpOptions(HttpOptions.builder()
                        .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                        .apiVersion("v1beta").timeout(5000).build()).build();
        GeminiConfiguration configuration = new GeminiConfiguration();
        var sharedClient = configuration.geminiChatClient(client);
        var properties = new com.hc.aiagenthyc.attachment.config.AttachmentProperties();
        properties.setDirectory(directory.toString());
        attachments = new com.hc.aiagenthyc.attachment.service.AttachmentService(
                new com.hc.aiagenthyc.attachment.storage.LocalAttachmentStorage(properties, new ObjectMapper()), properties);
        app = com.hc.aiagenthyc.support.LoveAppTestFactory.create(Map.of(
                "gemini-2.5-pro", new ModelRoute(sharedClient, "gemini-2.5-pro", java.util.Set.of(com.hc.aiagenthyc.model.InputType.TEXT, com.hc.aiagenthyc.model.InputType.IMAGE)),
                "gemini-2.5-flash-lite", new ModelRoute(sharedClient, "gemini-2.5-flash-lite", java.util.Set.of(com.hc.aiagenthyc.model.InputType.TEXT, com.hc.aiagenthyc.model.InputType.IMAGE))),
                MessageWindowChatMemory.builder().build(),
                new LovePromptTemplates(new ClassPathResource("prompts/love/system.st"),
                        new ClassPathResource("prompts/love/report-instruction.st")), "gemini-2.5-pro", attachments, properties);
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.stop(0);
    }

    @Test
    void sharedClientUsesPerRequestModelAndPreservesDefault() throws Exception {
        for (String model : new String[]{"gemini-2.5-pro", "gemini-2.5-flash-lite", ""}) {
            String expected = model.isEmpty() ? "gemini-2.5-pro" : model;
            String chatId = UUID.randomUUID().toString();
            String answer = model.isEmpty() ? app.doChat("Hello", chatId) : app.doChat("Hello", chatId, model);
            assertThat(answer).isEqualTo("Hello");
            CapturedRequest request = requests.poll(1, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.path()).isEqualTo("/v1beta/models/" + expected + ":generateContent");
            assertThat(request.apiKey()).isEqualTo("test-key");
            var json = new ObjectMapper().readTree(request.body());
            assertThat(json.at("/contents/0/parts/0/text").asText()).isEqualTo("Hello");
            assertThat(json.at("/systemInstruction/parts/0/text").asText()).isNotBlank();
        }
    }

    @Test
    void concurrentRequestsDoNotOverwriteEachOthersModel() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Callable<String>>();
            for (int i = 0; i < 8; i++) {
                String model = i % 2 == 0 ? "gemini-2.5-pro" : "gemini-2.5-flash-lite";
                tasks.add(() -> app.doChat(model, UUID.randomUUID().toString(), model));
            }
            for (var result : executor.invokeAll(tasks)) {
                assertThat(result.get()).isEqualTo("Hello");
            }
        }
        assertThat(requests).hasSize(8);
        for (CapturedRequest request : requests) {
            String requestedModel = new ObjectMapper().readTree(request.body())
                    .at("/contents/0/parts/0/text").asText();
            assertThat(request.path()).isEqualTo("/v1beta/models/" + requestedModel + ":generateContent");
        }
    }

    @Test
    void sendsActualImageBytesAndReusesImageInFollowup() throws Exception {
        byte[] bytes = com.hc.aiagenthyc.support.ImageFixtures.image("png", 2, 2);
        var file = attachments.upload(new org.springframework.mock.web.MockMultipartFile("file", bytes));
        app.doChat(new com.hc.aiagenthyc.app.model.LoveChatRequest("image-chat", null, "Look at the image",
                java.util.List.of(new com.hc.aiagenthyc.app.model.ChatAttachment(file.fileId()))));
        app.doChat("What about the last sentence?", "image-chat", "gemini-2.5-flash-lite");
        for (int i = 0; i < 2; i++) {
            var captured = requests.poll(1, TimeUnit.SECONDS);
            assertThat(captured).isNotNull();
            var parts = new ObjectMapper().readTree(captured.body()).at("/contents/0/parts");
            var inline = java.util.stream.StreamSupport.stream(parts.spliterator(), false)
                    .filter(part -> part.has("inlineData")).findFirst().orElseThrow().get("inlineData");
            assertThat(inline.get("mimeType").asText()).isEqualTo("image/png");
            assertThat(java.util.Base64.getDecoder().decode(inline.get("data").asText())).isEqualTo(bytes);
        }
    }

    private record CapturedRequest(String path, String body, String apiKey) { }
}
