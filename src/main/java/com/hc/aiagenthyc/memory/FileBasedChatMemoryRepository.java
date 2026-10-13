package com.hc.aiagenthyc.memory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.Assert;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * 使用 Kryo 将 Spring AI 对话消息持久化到本地文件的仓库。
 */
public final class FileBasedChatMemoryRepository implements ChatMemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(FileBasedChatMemoryRepository.class);

    private static final String FILE_SUFFIX = ".kryo";

    private static final int MAX_CONVERSATION_ID_BYTES = 128;

    private static final int LOCK_STRIPES = 64;

    private final Path baseDirectory;

    private final ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];

    private final ThreadLocal<Kryo> kryo = ThreadLocal.withInitial(FileBasedChatMemoryRepository::createKryo);

    public FileBasedChatMemoryRepository(String directory) {
        Assert.hasText(directory, "directory cannot be null or empty");
        this.baseDirectory = Path.of(directory).toAbsolutePath().normalize();

        for (int index = 0; index < this.locks.length; index++) {
            this.locks[index] = new ReentrantLock();
        }

        try {
            Files.createDirectories(this.baseDirectory);
            if (!Files.isDirectory(this.baseDirectory)) {
                throw new IllegalStateException("Chat memory path is not a directory: " + this.baseDirectory);
            }
        }
        catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize chat memory directory: " + this.baseDirectory,
                    exception);
        }
    }

    @Override
    public List<String> findConversationIds() {
        try (Stream<Path> files = Files.list(this.baseDirectory)) {
            return files
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.getFileName().toString())
                    .filter(fileName -> fileName.endsWith(FILE_SUFFIX))
                    .map(this::decodeConversationId)
                    .flatMap(List::stream)
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
        catch (Exception exception) {
            throw new IllegalStateException("Failed to list chat memory files in: " + this.baseDirectory, exception);
        }
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        validateConversationId(conversationId);
        ReentrantLock lock = lockFor(conversationId);
        lock.lock();
        try {
            Path conversationFile = conversationFile(conversationId);
            if (Files.notExists(conversationFile)) {
                return List.of();
            }
            rejectSymbolicLink(conversationFile);

            try (Input input = new Input(new BufferedInputStream(Files.newInputStream(conversationFile)))) {
                List<?> storedMessages = this.kryo.get().readObject(input, ArrayList.class);
                List<Message> messages = new ArrayList<>(storedMessages.size());
                for (Object storedMessage : storedMessages) {
                    if (!(storedMessage instanceof Message message)) {
                        throw new IllegalStateException("Invalid object in chat memory file: " + conversationFile);
                    }
                    messages.add(message);
                }
                return messages;
            }
        }
        catch (Exception exception) {
            throw new IllegalStateException("Failed to read chat memory for conversation: " + conversationId,
                    exception);
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        validateConversationId(conversationId);
        Assert.notNull(messages, "messages cannot be null");
        Assert.noNullElements(messages, "messages cannot contain null elements");

        ReentrantLock lock = lockFor(conversationId);
        lock.lock();
        Path temporaryFile = null;
        try {
            Path conversationFile = conversationFile(conversationId);
            if (Files.exists(conversationFile)) {
                rejectSymbolicLink(conversationFile);
            }

            temporaryFile = Files.createTempFile(this.baseDirectory,
                    conversationFile.getFileName().toString(), ".tmp");
            try (Output output = new Output(new BufferedOutputStream(Files.newOutputStream(temporaryFile,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)))) {
                this.kryo.get().writeObject(output, new ArrayList<>(messages));
            }

            moveAtomically(temporaryFile, conversationFile);
            temporaryFile = null;
        }
        catch (Exception exception) {
            throw new IllegalStateException("Failed to save chat memory for conversation: " + conversationId,
                    exception);
        }
        finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                }
                catch (Exception exception) {
                    log.warn("Failed to delete temporary chat memory file: {}", temporaryFile, exception);
                }
            }
            lock.unlock();
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        validateConversationId(conversationId);
        ReentrantLock lock = lockFor(conversationId);
        lock.lock();
        try {
            Path conversationFile = conversationFile(conversationId);
            if (Files.exists(conversationFile)) {
                rejectSymbolicLink(conversationFile);
                Files.delete(conversationFile);
            }
        }
        catch (Exception exception) {
            throw new IllegalStateException("Failed to delete chat memory for conversation: " + conversationId,
                    exception);
        }
        finally {
            lock.unlock();
        }
    }

    private static Kryo createKryo() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
        return kryo;
    }

    private void validateConversationId(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        int byteLength = conversationId.getBytes(StandardCharsets.UTF_8).length;
        Assert.isTrue(byteLength <= MAX_CONVERSATION_ID_BYTES,
                "conversationId must not exceed " + MAX_CONVERSATION_ID_BYTES + " UTF-8 bytes");
    }

    private Path conversationFile(String conversationId) {
        String encodedId = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(conversationId.getBytes(StandardCharsets.UTF_8));
        return this.baseDirectory.resolve(encodedId + FILE_SUFFIX);
    }

    private List<String> decodeConversationId(String fileName) {
        String encodedId = fileName.substring(0, fileName.length() - FILE_SUFFIX.length());
        try {
            String conversationId = new String(Base64.getUrlDecoder().decode(encodedId), StandardCharsets.UTF_8);
            validateConversationId(conversationId);
            return List.of(conversationId);
        }
        catch (Exception exception) {
            log.warn("Ignoring invalid chat memory file: {}", this.baseDirectory.resolve(fileName));
            return List.of();
        }
    }

    private ReentrantLock lockFor(String conversationId) {
        return this.locks[Math.floorMod(conversationId.hashCode(), this.locks.length)];
    }

    private void rejectSymbolicLink(Path file) {
        if (Files.isSymbolicLink(file)) {
            throw new IllegalStateException("Symbolic links are not allowed for chat memory files: " + file);
        }
    }

    private void moveAtomically(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException exception) {
            log.warn("Atomic move is not supported for {}; using a regular replacement move", target);
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
