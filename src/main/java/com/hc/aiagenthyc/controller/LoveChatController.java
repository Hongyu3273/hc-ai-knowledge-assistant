package com.hc.aiagenthyc.controller;

import com.hc.aiagenthyc.app.LoveApp;
import com.hc.aiagenthyc.app.model.LoveChatRequest;
import com.hc.aiagenthyc.app.model.LoveReport;
import com.hc.aiagenthyc.app.model.LoveReportRequest;
import com.hc.aiagenthyc.model.ModelCatalog;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/love")
public class LoveChatController {
    private final LoveApp app;
    private final ModelCatalog catalog;
    public LoveChatController(LoveApp app, ModelCatalog catalog) { this.app = app; this.catalog = catalog; }

    @Operation(summary = "Multimodal relationship counseling", description = "Upload files through /attachments to obtain attachment references; reuse the same chatId to continue a conversation")
    @PostMapping("/chat")
    public ChatReply chat(@Valid @RequestBody LoveChatRequest request) {
        String model = catalog.resolve(request.model());
        return new ChatReply(request.chatId(), model, app.doChat(request));
    }
    @Operation(summary = "Generate a structured relationship counseling report")
    @PostMapping("/report")
    public ReportReply report(@Valid @RequestBody LoveReportRequest request) {
        String model = catalog.resolve(request.model());
        return new ReportReply(request.chatId(), model, app.doChatWithReport(request));
    }
    @Operation(summary = "Relationship counseling with document retrieval")
    @PostMapping("/rag")
    public ChatReply rag(@Valid @RequestBody RagRequest request) {
        return new ChatReply(request.chatId(), catalog.resolve(null), app.doChatWithRag(request.message(), request.chatId()));
    }
    public record ChatReply(String chatId, String model, String content) { }
    public record ReportReply(String chatId, String model, LoveReport report) { }
    public record RagRequest(@NotBlank @Size(max = 128) String chatId,
                             @NotBlank @Size(max = 20000) String message) { }
}
