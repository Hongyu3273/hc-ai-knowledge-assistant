# Architecture and design decisions

## Request lifecycle

1. A controller accepts a chat, report, or image-upload request.
2. Validation checks required fields, text length, attachment count, and the selected model.
3. `ConversationExecutor` serializes work on the same conversation within this JVM.
4. `LoveApp` reads the retained message window and builds the outgoing text/image message. Attachment IDs are stored as metadata; image bytes are loaded only for a model call.
5. `ModelCatalog` selects a route. The configured `ChatClient` sends the request through the text-checking Advisor to DashScope, Gemini, or an available local route.
6. The response is checked. A structured report is parsed and validated before it is returned.
7. On success, the new user message and assistant message are appended together to chat memory. Failed calls do not add a partial turn.

## Components

| Package | Main classes | Design purpose |
| --- | --- | --- |
| `controller` | `LoveChatController`, `AttachmentController`, `ModelController` | HTTP contract |
| `app` | `LoveApp`, validator, assembler, prompt templates | Business orchestration |
| `model` | `ModelCatalog`, `ModelRoute`, `InputType` | Provider-independent model selection |
| `attachment` | `AttachmentService`, `LocalAttachmentStorage` | File validation and storage |
| `memory` | `ConversationExecutor`, `SynchronizedChatMemory`, file repository | Ordered, bounded context |
| `config` | Gemini, model, and memory configuration | Dependency assembly |
| `advisor` | `VerifyAdvisor` | Example text interception |

Prompt templates live under `src/main/resources/prompts/love/`, separate from Java orchestration. The templates currently contain Chinese instructions because the demonstrated advice scenario is Chinese-language; source documentation is in English.

## Persistence

`MessageWindowChatMemory` keeps at most the configured number of recent messages. `FileBasedChatMemoryRepository` serializes the current list with Kryo into one file per conversation. The ID is encoded for a safe filename; writes use a temporary file and replacement. The memory window is context for the model, not a complete audit history.

`SynchronizedChatMemory` protects individual memory operations. `ConversationExecutor` covers the larger read → model call → write sequence for a conversation. Both operate within one JVM; neither is a distributed lock.

Uploaded images are stored separately and referenced by generated IDs in user-message metadata. A later turn can rehydrate retained images. If a referenced file disappears, the request fails instead of silently dropping the image.

## Scope and trade-offs

- The backend has no authentication or tenant isolation. Conversation and attachment IDs must not be treated as authorization tokens.
- Local files and locks suit a single instance. Multi-instance deployment requires shared storage and cross-instance coordination.
- The example blocklist is deterministic substring matching. It can reject harmless uses of a word and does not analyze image content.
- Attachment expiry and disk-wide quotas are not implemented.
- MySQL code is an optional prototype. The running application does not use MySQL.
- There is no vector search or retrieval-augmented generation pipeline yet.

## Testing

`./mvnw clean test` runs the offline suite. Tests cover upload formats and limits, model capability checks, remembered images, failed-call memory behavior, structured reports, concurrent requests, HTTP errors, and Google SDK requests against a local stub. Live model calls require explicit opt-in and valid credentials.
