package com.hc.aiagenthyc.app.service;

import com.hc.aiagenthyc.app.model.AssistantChatRequest;
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

/**
 * Converts between the user messages kept in chat memory and the messages sent to a model.
 *
 * <p>Stored messages carry attachment IDs in their metadata instead of image bytes. Before each call,
 * {@link #assemble} reloads those images from attachment storage and checks them against the selected route's
 * input types and the per-conversation size limit.</p>
 */
@Component
public class MultimodalMessageAssembler {
    /** User-message metadata key holding the list of attachment IDs ({@code List<String>}) for that turn. */
    public static final String ATTACHMENT_IDS = "assistant.attachmentIds";
    private final AttachmentService attachments;
    private final AttachmentProperties properties;
    public MultimodalMessageAssembler(AttachmentService attachments, AttachmentProperties properties) {
        this.attachments = attachments;
        this.properties = properties;
    }

    /** Persist references rather than image bytes. Use ordinary serializable collections for compatibility with the Kryo repository. */
    public UserMessage storedMessage(AssistantChatRequest request, String text) {
        Map<String, Object> metadata = new HashMap<>();
        if (!request.attachments().isEmpty()) metadata.put(ATTACHMENT_IDS,
                new ArrayList<>(request.attachments().stream().map(a -> a.fileId()).toList()));
        return UserMessage.builder().text(text).metadata(metadata).build();
    }

    /**
     * Builds the message list for one model call from the conversation history and the current user message.
     *
     * <p>Non-user messages pass through unchanged. Each user message is rebuilt with its text and hydrated media only,
     * so attachment IDs are not sent to the model. The inputs are not modified.</p>
     *
     * @param history   messages read from chat memory
     * @param current   the outgoing user message, typically from {@link #storedMessage}
     * @param supported input types of the selected model route
     * @return a new list ready to pass to the chat client
     * @throws ChatException {@link ErrorCode#MODEL_INPUT_NOT_SUPPORTED} if the route cannot accept text or an image in
     *                       the history; {@link ErrorCode#ATTACHMENT_UNAVAILABLE} for a missing file or malformed
     *                       reference; {@link ErrorCode#UNSUPPORTED_ATTACHMENT_TYPE} for a type no longer allowed;
     *                       {@link ErrorCode#ATTACHMENT_TOO_LARGE} if all images together exceed the context limit
     */
    public List<Message> assemble(List<Message> history, UserMessage current, Set<InputType> supported) {
        // Rebuild media from stored attachment IDs for this call without mutating persisted history.
        // Validate historical images too: switching to a text-only route cannot reuse image context.
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
                // Legacy memory contains text only; never fetch arbitrary external URIs from stored messages.
                if (!(existing.getData() instanceof byte[] bytes))
                    throw new ChatException(ErrorCode.ATTACHMENT_UNAVAILABLE, "The media format in conversation history is unsupported. Start a new conversation.");
                total += bytes.length;
            }
            Object raw = user.getMetadata().get(ATTACHMENT_IDS);
            if (raw != null && !(raw instanceof List<?>)) throw unavailable();
            if (raw instanceof List<?> ids) {
                for (Object value : ids) {
                    if (!(value instanceof String id)) throw unavailable();
                    var metadata = attachments.metadata(id);
                    if (!properties.getAllowedTypes().contains(metadata.mimeType()))
                        throw new ChatException(ErrorCode.UNSUPPORTED_ATTACHMENT_TYPE, "The attachment type in conversation history is no longer supported");
                    require(supported, inputType(metadata.mimeType()));
                    total += metadata.size();
                    // Check before reading so an oversized conversation fails without loading the bytes.
                    checkSize(total);
                    media.add(new Media(MimeTypeUtils.parseMimeType(metadata.mimeType()), new org.springframework.core.io.ByteArrayResource(attachments.read(id))));
                }
            }
            checkSize(total);
            // Keep internal file references out of model requests and do not persist the hydrated media objects.
            result.add(UserMessage.builder().text(user.getText()).media(media).build());
        }
        return result;
    }
    private void checkSize(long bytes) {
        if (bytes > properties.getMaxContextBytes()) throw new ChatException(ErrorCode.ATTACHMENT_TOO_LARGE,
                "The total image size in this conversation exceeds the limit. Start a new conversation.");
    }
    private static InputType inputType(String mime) {
        if (mime.startsWith("image/")) return InputType.IMAGE;
        if (mime.startsWith("audio/")) return InputType.AUDIO;
        throw new ChatException(ErrorCode.UNSUPPORTED_ATTACHMENT_TYPE, "Unsupported media type");
    }
    private static void require(Set<InputType> supported, InputType type) {
        if (!supported.contains(type)) throw new ChatException(ErrorCode.MODEL_INPUT_NOT_SUPPORTED,
                "The selected model does not support this input type in the current message or history: " + type.value() + ". Select a compatible model or start a new conversation.");
    }
    private static ChatException unavailable() {
        return new ChatException(ErrorCode.ATTACHMENT_UNAVAILABLE, "An attachment reference in conversation history is invalid. Start a new conversation.");
    }
}
