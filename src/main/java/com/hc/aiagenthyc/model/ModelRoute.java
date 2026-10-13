package com.hc.aiagenthyc.model;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import java.util.Set;

/** 同一个客户端可以对应多个型号；null 表示使用供应商配置的默认型号。 */
public record ModelRoute(ChatClient client, String model, Set<InputType> supportedInputs) {
    public ModelRoute {
        java.util.Objects.requireNonNull(client, "client cannot be null");
        supportedInputs = Set.copyOf(supportedInputs);
        if (!supportedInputs.contains(InputType.TEXT) || supportedInputs.contains(InputType.AUDIO)) {
            throw new IllegalArgumentException("当前应用支持 text 或 text + image 输入，音频暂未开放");
        }
    }
    public ModelRoute(ChatClient client, String model) {
        this(client, model, Set.of(InputType.TEXT));
    }
    public ChatClient.ChatClientRequestSpec prompt() {
        var request = client.prompt();
        return model == null ? request : request.options(ChatOptions.builder().model(model).build());
    }
}
