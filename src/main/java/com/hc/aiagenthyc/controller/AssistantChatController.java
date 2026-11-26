package com.hc.aiagenthyc.controller;

import com.hc.aiagenthyc.app.KnowledgeAssistant;
import com.hc.aiagenthyc.app.model.AssistantChatRequest;
import com.hc.aiagenthyc.app.model.StudyReport;
import com.hc.aiagenthyc.app.model.StudyReportRequest;
import com.hc.aiagenthyc.model.ModelCatalog;
import com.hc.aiagenthyc.rag.RagAnswer;
import com.hc.aiagenthyc.rag.RagSource;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST entry point of the interview-preparation assistant under {@code /api/assistant}.
 * Delegates to {@link KnowledgeAssistant}; conversation memory is keyed by {@code chatId}, and failures are turned
 * into error responses by {@code ChatExceptionHandler}.
 */
@RestController
@RequestMapping("/assistant")
public class AssistantChatController {
    private final KnowledgeAssistant app;
    private final ModelCatalog catalog;
    public AssistantChatController(KnowledgeAssistant app, ModelCatalog catalog) { this.app = app; this.catalog = catalog; }

    /**
     * {@code POST /api/assistant/chat}: sends one text and/or image message in a conversation and returns the answer.
     * Images must first be uploaded through {@code POST /api/attachments}. The reply names the model route used;
     * an omitted model selects the configured default and an unknown one fails with {@code MODEL_NOT_AVAILABLE}.
     */
    @Operation(summary = "Multimodal interview-preparation chat", description = "Upload files through /attachments to obtain attachment references; reuse the same chatId to continue a conversation")
    @PostMapping("/chat")
    public ChatReply chat(@Valid @RequestBody AssistantChatRequest request) {
        String model = catalog.resolve(request.model());
        return new ChatReply(request.chatId(), model, app.doChat(request));
    }
    /**
     * {@code POST /api/assistant/report}: like {@code /chat}, but asks the model for a structured study report and
     * returns it as a parsed {@link StudyReport}.
     */
    @Operation(summary = "Generate a structured study report")
    @PostMapping("/report")
    public ReportReply report(@Valid @RequestBody StudyReportRequest request) {
        String model = catalog.resolve(request.model());
        return new ReportReply(request.chatId(), model, app.doChatWithReport(request));
    }
    /**
     * {@code POST /api/assistant/rag}: answers a text question from the local question-bank index with the default
     * model and returns the retrieved passages as sources.
     */
    @Operation(summary = "Answer from the interview question banks",
            description = "Answers from the bundled question banks and returns the questions that were supplied to the model")
    @PostMapping("/rag")
    public RagReply rag(@Valid @RequestBody RagRequest request) {
        RagAnswer answer = app.doChatWithRag(request.message(), request.chatId());
        // RAG always uses the default route, so report the default model ID.
        return new RagReply(request.chatId(), catalog.resolve(null), answer.content(), answer.sources());
    }
    /**
     * Response of {@code /chat}.
     *
     * @param chatId conversation ID from the request
     * @param model route ID that answered
     * @param content model answer
     */
    public record ChatReply(String chatId, String model, String content) { }
    /**
     * Response of {@code /report}.
     *
     * @param chatId conversation ID from the request
     * @param model route ID that answered
     * @param report structured report parsed from the model output
     */
    public record ReportReply(String chatId, String model, StudyReport report) { }
    /**
     * Response of the local and cloud RAG endpoints.
     *
     * @param chatId conversation ID from the request
     * @param model default route ID that answered
     * @param content model answer
     * @param sources passages that were supplied to the model; empty when nothing was retrieved
     */
    public record RagReply(String chatId, String model, String content, List<RagSource> sources) { }
    /**
     * Text-only request of the local and cloud RAG endpoints.
     *
     * @param chatId conversation ID; required, at most 128 characters
     * @param message question text; required, at most 20,000 characters
     */
    public record RagRequest(@NotBlank @Size(max = 128) String chatId,
                             @NotBlank @Size(max = 20000) String message) { }
}
