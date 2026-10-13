package com.hc.aiagenthyc.memory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Stores each conversation's current memory window in MySQL. */
public final class MySqlChatMemoryRepository implements ChatMemoryRepository {

    private final DataSource dataSource;
    private final ThreadLocal<Kryo> kryo = ThreadLocal.withInitial(MySqlChatMemoryRepository::createKryo);

    public MySqlChatMemoryRepository(DataSource dataSource) {
        Assert.notNull(dataSource, "dataSource cannot be null");
        this.dataSource = dataSource;
    }

    @Override
    public List<String> findConversationIds() {
        String sql = "SELECT conversation_id FROM chat_memory ORDER BY conversation_id";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            List<String> ids = new ArrayList<>();
            while (result.next()) {
                ids.add(result.getString(1));
            }
            return ids;
        }
        catch (SQLException exception) {
            throw new IllegalStateException("Failed to list chat memory conversations", exception);
        }
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        validateConversationId(conversationId);
        String sql = "SELECT messages FROM chat_memory WHERE conversation_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, conversationId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? deserialize(result.getBytes(1)) : List.of();
            }
        }
        catch (SQLException exception) {
            throw new IllegalStateException("Failed to read chat memory for conversation: " + conversationId,
                    exception);
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        validateConversationId(conversationId);
        Assert.notNull(messages, "messages cannot be null");
        Assert.noNullElements(messages, "messages cannot contain null elements");
        String sql = "INSERT INTO chat_memory (conversation_id, messages) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE messages = VALUES(messages)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, conversationId);
            statement.setBytes(2, serialize(messages));
            statement.executeUpdate();
        }
        catch (SQLException exception) {
            throw new IllegalStateException("Failed to save chat memory for conversation: " + conversationId,
                    exception);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        validateConversationId(conversationId);
        String sql = "DELETE FROM chat_memory WHERE conversation_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, conversationId);
            statement.executeUpdate();
        }
        catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete chat memory for conversation: " + conversationId,
                    exception);
        }
    }

    private byte[] serialize(List<Message> messages) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Output output = new Output(bytes)) {
            kryo.get().writeObject(output, new ArrayList<>(messages));
        }
        return bytes.toByteArray();
    }

    private List<Message> deserialize(byte[] data) {
        if (data == null) {
            throw new IllegalStateException("Chat memory data cannot be null");
        }
        try (Input input = new Input(new ByteArrayInputStream(data))) {
            List<?> stored = kryo.get().readObject(input, ArrayList.class);
            List<Message> messages = new ArrayList<>(stored.size());
            for (Object value : stored) {
                if (!(value instanceof Message message)) {
                    throw new IllegalStateException("Invalid object in chat memory data");
                }
                messages.add(message);
            }
            return messages;
        }
    }

    private static Kryo createKryo() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
        return kryo;
    }

    private static void validateConversationId(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.isTrue(conversationId.getBytes(StandardCharsets.UTF_8).length <= 128,
                "conversationId must not exceed 128 UTF-8 bytes");
    }
}
