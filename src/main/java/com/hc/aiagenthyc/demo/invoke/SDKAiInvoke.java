package com.hc.aiagenthyc.demo.invoke;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.utils.Constants;
import com.alibaba.dashscope.utils.JsonUtils;

import java.util.Arrays;
import java.util.Collections;

public class SDKAiInvoke {
    // 建议dashscope SDK的版本 >= 2.12.0


    static {
        Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";
    }

    public static MultiModalConversationResult callWithMessage() throws ApiException, NoApiKeyException, InputRequiredException, UploadFileException {
        MultiModalConversation conv = new MultiModalConversation();
        MultiModalMessage systemMsg = MultiModalMessage.builder()
                .role(Role.SYSTEM.getValue())
                .content(Arrays.asList(Collections.singletonMap("text", "You are a helpful assistant.")))
                .build();
        MultiModalMessage userMsg = MultiModalMessage.builder()
                .role(Role.USER.getValue())
                .content(Arrays.asList(Collections.singletonMap("text", "你是谁？")))
                .build();
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                // 引用 TestApiKey 中的常量（本地测试用；生产建议改回环境变量）
                .apiKey(TestApiKey.API_KEY)
                // 此处以qwen3.8-max为例，可按需更换模型名称。模型列表：https://help.aliyun.com/zh/model-studio/getting-started/models
                .model("qwen3.8-max")
                .messages(Arrays.asList(systemMsg, userMsg))
                .build();
        return conv.call(param);
    }

    public static void main(String[] args) throws ApiException, NoApiKeyException, InputRequiredException, UploadFileException {
        MultiModalConversationResult result = callWithMessage();
        System.out.println(JsonUtils.toJson(result));
    }
}
