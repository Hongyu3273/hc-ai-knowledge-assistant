package com.hc.aiagenthyc.app.service;

import com.hc.aiagenthyc.app.model.LoveChatRequest;
import com.hc.aiagenthyc.attachment.config.AttachmentProperties;
import com.hc.aiagenthyc.attachment.service.AttachmentService;
import com.hc.aiagenthyc.common.error.ChatException;
import com.hc.aiagenthyc.common.error.ErrorCode;
import com.hc.aiagenthyc.model.InputType;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import java.util.*;

@Component
public class MultimodalMessageAssembler {
    public static final String ATTACHMENT_IDS = "love.attachmentIds";
    private final AttachmentService attachments;
    private final AttachmentProperties properties;
    public MultimodalMessageAssembler(AttachmentService attachments, AttachmentProperties properties) {
        this.attachments = attachments;
        this.properties = properties;
    }

    /** 持久化消息仅包含引用，不包含图片字节。使用可序列化的普通集合兼容已有 Kryo 仓库。 */
    public UserMessage storedMessage(LoveChatRequest request, String text) {
        Map<String, Object> metadata = new HashMap<>();
        if (!request.attachments().isEmpty()) metadata.put(ATTACHMENT_IDS,
                new ArrayList<>(request.attachments().stream().map(a -> a.fileId()).toList()));
        return UserMessage.builder().text(text).metadata(metadata).build();
    }

    public List<Message> assemble(List<Message> history, UserMessage current, Set<InputType> supported) {
        List<Message> stored = new ArrayList<>(history);
        stored.add(current);
        List<Message> result = new ArrayList<>();
        long total = 0;
        require(supported, InputType.TEXT);
        for (Message message : stored) {
            if (!(message instanceof UserMessage user)) {
                result.add(message);
                continue;
            }
            List<Media> media = new ArrayList<>(user.getMedia());
            for (Media existing : media) {
                require(supported, inputType(existing.getMimeType().toString()));
                // 第一版旧仓库只有文字；不自动读取历史中的任意外部 URI。
                if (!(existing.getData() instanceof byte[] bytes))
                    throw new ChatException(ErrorCode.ATTACHMENT_UNAVAILABLE, "历史媒体格式不受支持，请新建会话");
                total += bytes.length;
            }
            Object raw = user.getMetadata().get(ATTACHMENT_IDS);
            if (raw != null && !(raw instanceof List<?>)) throw unavailable();
            if (raw instanceof List<?> ids) {
                for (Object value : ids) {
                    if (!(value instanceof String id)) throw unavailable();
                    var metadata = attachments.metadata(id);
                    if (!properties.getAllowedTypes().contains(metadata.mimeType()))
                        throw new ChatException(ErrorCode.UNSUPPORTED_ATTACHMENT_TYPE, "历史附件类型已不受支持");
                    require(supported, inputType(metadata.mimeType()));
                    total += metadata.size();
                    checkSize(total);
                    media.add(new Media(MimeTypeUtils.parseMimeType(metadata.mimeType()), new org.springframework.core.io.ByteArrayResource(attachments.read(id))));
                }
            }
            checkSize(total);
            // 文件引用是内部信息，不发送给模型，也不将加载后的媒体对象保存回仓库。
            result.add(UserMessage.builder().text(user.getText()).media(media).build());
        }
        return result;
    }
    private void checkSize(long bytes) {
        if (bytes > properties.getMaxContextBytes()) throw new ChatException(ErrorCode.ATTACHMENT_TOO_LARGE,
                "会话中的图片总大小超过限制，请新建会话");
    }
    private static InputType inputType(String mime) {
        if (mime.startsWith("image/")) return InputType.IMAGE;
        if (mime.startsWith("audio/")) return InputType.AUDIO;
        throw new ChatException(ErrorCode.UNSUPPORTED_ATTACHMENT_TYPE, "不支持的媒体类型");
    }
    private static void require(Set<InputType> supported, InputType type) {
        if (!supported.contains(type)) throw new ChatException(ErrorCode.MODEL_INPUT_NOT_SUPPORTED,
                "所选模型不支持当前消息或历史消息的 " + type.value() + " 输入，请选择兼容模型或新建会话");
    }
    private static ChatException unavailable() {
        return new ChatException(ErrorCode.ATTACHMENT_UNAVAILABLE, "历史附件引用无效，请新建会话");
    }
}
