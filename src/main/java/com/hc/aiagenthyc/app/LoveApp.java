package com.hc.aiagenthyc.app;

import com.hc.aiagenthyc.model.ModelRoute;

import com.hc.aiagenthyc.advisor.VerifyAdvisor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.hc.aiagenthyc.app.model.LoveChatRequest;
import com.hc.aiagenthyc.app.model.LoveReport;
import com.hc.aiagenthyc.app.model.LoveReportRequest;
import com.hc.aiagenthyc.app.prompt.LovePromptTemplates;
import com.hc.aiagenthyc.app.service.ChatRequestValidator;
import com.hc.aiagenthyc.app.service.MultimodalMessageAssembler;
import com.hc.aiagenthyc.common.error.ChatException;
import com.hc.aiagenthyc.common.error.ErrorCode;
import com.hc.aiagenthyc.config.ChatMemoryConfiguration;
import com.hc.aiagenthyc.memory.ConversationExecutor;
import com.hc.aiagenthyc.model.ModelCatalog;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 恋爱助手用例入口：统一模型选择、媒体上下文和成功后的会话提交。 */
@Service
public class LoveApp {
    private final Map<String, ModelRoute> clients;
    private final ObjectMapper objectMapper;
    private final ModelCatalog catalog;
    private final ChatMemory memory;
    private final LovePromptTemplates templates;
    private final ChatRequestValidator validator;
    private final MultimodalMessageAssembler assembler;
    private final ConversationExecutor conversations;

    public LoveApp(ModelCatalog catalog,
                   @Qualifier(ChatMemoryConfiguration.CHAT_MEMORY_BEAN_NAME) ChatMemory memory,
                   LovePromptTemplates templates, ChatRequestValidator validator,
                   MultimodalMessageAssembler assembler, ConversationExecutor conversations, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.catalog = catalog;
        this.memory = memory;
        this.templates = templates;
        this.validator = validator;
        this.assembler = assembler;
        this.conversations = conversations;
        Map<ChatClient, ChatClient> businessClients = new IdentityHashMap<>();
        this.clients = catalog.routes().entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, e -> new ModelRoute(
                        businessClients.computeIfAbsent(e.getValue().client(), client -> client.mutate()
                                .defaultSystem(templates.renderSystemPrompt())
                                .defaultAdvisors(new VerifyAdvisor()).build()),
                        e.getValue().model(), e.getValue().supportedInputs())));
    }

    public String doChat(LoveChatRequest request) {
        return execute(request, null, null, false, Function.identity());
    }

    public String doChat(String message, String chatId) { return doChat(message, chatId, null); }
    public String doChat(String message, String chatId, String model) {
        return doChat(new LoveChatRequest(chatId, model, message, List.of()));
    }

    public LoveReport doChatWithReport(LoveReportRequest request) {
        validator.validate(request);
        return execute(request.chatRequest(), request.userName(), request.relationshipStatus(), true, this::parseReport);
    }

    public Set<String> availableModels() { return clients.keySet(); }

    private LoveReport parseReport(String text) {
        LoveReport report;
        try {
            String json = text.strip();
            if (json.startsWith("```") && json.endsWith("```")) {
                int newline = json.indexOf('\n');
                if (newline < 0) throw new IllegalStateException("Invalid report response");
                json = json.substring(newline + 1, json.length() - 3).strip();
            }
            report = objectMapper.readValue(json, LoveReport.class);
        } catch (JsonProcessingException e) {
            // 不记录原始模型输出，避免在解析失败日志中泄露聊天内容。
            throw new IllegalStateException("Invalid report response");
        }
        if (report == null || !StringUtils.hasText(report.title()) || report.suggestions() == null
                || report.suggestions().isEmpty() || report.suggestions().stream().anyMatch(s -> !StringUtils.hasText(s)))
            throw new IllegalStateException("Invalid report response");
        return report;
    }

    private <T> T execute(LoveChatRequest request, String userName, String relationshipStatus,
                          boolean report, Function<String, T> converter) {
        validator.validate(request);
        String model = catalog.resolve(request.model());
        ModelRoute route = clients.get(model);
        return conversations.execute(request.chatId(), () -> {
            String text = StringUtils.hasText(request.text()) ? request.text() : "请描述并分析这张图片。";
            if (report) text = templates.renderReportInstruction(userName, relationshipStatus, text);
            UserMessage stored = assembler.storedMessage(request, text);
            // 输出格式只用于本次调用，存储的用户消息不重复包含 schema。
            UserMessage outgoing = report ? assembler.storedMessage(request,
                    text + "\n" + new BeanOutputConverter<>(LoveReport.class).getFormat()) : stored;
            var messages = assembler.assemble(memory.get(request.chatId()), outgoing, route.supportedInputs());
            String answer;
            T result;
            try {
                answer = route.prompt().messages(messages).call().content();
                if (!StringUtils.hasText(answer)) throw new IllegalStateException("Empty model response");
                result = converter.apply(answer);
            } catch (ChatException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ChatException(ErrorCode.MODEL_CALL_FAILED, "模型调用失败或返回格式无效，请稍后重试");
            }
            // 一次提交本轮两条消息。校验、模型调用、报告解析失败时不修改历史。
            memory.add(request.chatId(), List.of(stored, new AssistantMessage(answer)));
            return result;
        });
    }
}
