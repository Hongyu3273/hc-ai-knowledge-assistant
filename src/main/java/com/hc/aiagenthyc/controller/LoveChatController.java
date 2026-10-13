package com.hc.aiagenthyc.controller;

import com.hc.aiagenthyc.app.LoveApp;
import com.hc.aiagenthyc.app.model.LoveChatRequest;
import com.hc.aiagenthyc.app.model.LoveReport;
import com.hc.aiagenthyc.app.model.LoveReportRequest;
import com.hc.aiagenthyc.model.ModelCatalog;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/love")
public class LoveChatController {
    private final LoveApp app;
    private final ModelCatalog catalog;
    public LoveChatController(LoveApp app, ModelCatalog catalog) { this.app = app; this.catalog = catalog; }

    @Operation(summary = "多模态恋爱咨询", description = "附件引用通过 /attachments 上传获得；同一 chatId 延续上下文")
    @PostMapping("/chat")
    public ChatReply chat(@Valid @RequestBody LoveChatRequest request) {
        String model = catalog.resolve(request.model());
        return new ChatReply(request.chatId(), model, app.doChat(request));
    }
    @Operation(summary = "生成结构化恋爱咨询报告")
    @PostMapping("/report")
    public ReportReply report(@Valid @RequestBody LoveReportRequest request) {
        String model = catalog.resolve(request.model());
        return new ReportReply(request.chatId(), model, app.doChatWithReport(request));
    }
    public record ChatReply(String chatId, String model, String content) { }
    public record ReportReply(String chatId, String model, LoveReport report) { }
}
