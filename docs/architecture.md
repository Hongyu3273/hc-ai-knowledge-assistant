# Architecture and design decisions

## Request lifecycle

### Chat and report

1. A controller accepts a chat, report, or image-upload request.
2. Validation checks required fields, text length, attachment count, and the selected model.
3. `ConversationExecutor` serializes work on the same conversation within this JVM.
4. `KnowledgeAssistant` reads the retained message window and builds the outgoing text/image message. Attachment IDs are stored as metadata; image bytes are loaded only for a model call.
5. `ModelCatalog` selects a route. The configured `ChatClient` sends the request through the metrics and text-checking Advisors to DashScope, Gemini, or an available local route.
6. The response is checked. A structured report is parsed and validated before it is returned.
7. On success, the new user message and assistant message are appended together to chat memory. Failed calls do not add a partial turn.

### Retrieval-augmented chat (`doChatWithRag`, `POST /api/assistant/rag`)

1. The question is validated as a text-only chat request and the **default** model route is selected. The request cannot choose a model or attach an image.
2. The blocklist checks the user's question. A blocked question is rejected before any embedding call.
3. `ConversationExecutor` locks the conversation, as for ordinary chat.
4. The vector store returns up to `top-k` passages with similarity at or above `similarity-threshold`.
5. If passages were found, they are joined into a grounded prompt: reference material, then the question, with instructions to treat the material as information rather than instructions and to say when no relevant answer is found. If none were found, the prompt instead says the knowledge base had nothing relevant and lets the model use the conversation history.
6. The prompt is sent with the retained message window, through the system prompt, `MetricsAdvisor`, and `VerifyAdvisor`. `VerifyAdvisor` is told (through the advisor context) to check the user's question rather than the grounded prompt.
7. On success, memory stores the **original question** and the answer, and the response lists the supplied passages as `sources`. Retrieved text is not written to memory.
8. A retrieval or model failure is logged on the server and reported as `MODEL_CALL_FAILED`; no partial turn is saved.

### Cloud retrieval (`doChatWithRagCloud`, `POST /api/assistant/rag/cloud`, off by default)

`doChatWithRagCloud` follows the same validation, locking, memory, and error rules, but delegates retrieval to Spring AI's `RetrievalAugmentationAdvisor` backed by a `DashScopeDocumentRetriever`, which queries a knowledge-base index hosted in DashScope (Alibaba Cloud Model Studio). The Advisor adds retrieved content to the outgoing prompt for that call only and records the documents in the response context, from which the `sources` list is built. The Advisor bean and the endpoint exist only when `app.assistant.rag.cloud.enabled=true`; the index must be created and populated in the DashScope console.

## Components

| Package | Main classes | Design purpose |
| --- | --- | --- |
| `controller` | `AssistantChatController`, `AssistantRagCloudController` (conditional), `AttachmentController`, `ModelController` | HTTP contract |
| `app` | `KnowledgeAssistant`, validator, assembler, prompt templates | Business orchestration |
| `model` | `ModelCatalog`, `ModelRoute`, `InputType` | Provider-independent model selection |
| `attachment` | `AttachmentService`, `LocalAttachmentStorage` | File validation and storage |
| `memory` | `ConversationExecutor`, `SynchronizedChatMemory`, file repository | Ordered, bounded context |
| `rag` | `KnowledgeBaseLoader`, `KnowledgeBaseVectorStoreConfig`, `PersistentVectorIndex`, `CloudRagAdvisorConfig`, `RagProperties`, `RagEvaluator` | Knowledge loading, saved local index, cloud retrieval, settings, retrieval evaluation |
| `config` | Gemini, model, and memory configuration | Dependency assembly |
| `advisor` | `MetricsAdvisor`, `VerifyAdvisor`, `MyLoggerAdvisor`, `ReReadingAdvisor` | Request interception; `MetricsAdvisor` and `VerifyAdvisor` are always registered on business clients, `MyLoggerAdvisor` only when enabled |

Prompt templates live under `src/main/resources/prompts/assistant/`, separate from Java orchestration. The templates are in English; the system prompt asks the model to reply in the language the user writes in.

## Knowledge base and index

`KnowledgeBaseLoader` finds `classpath*:document/*.md` and parses each question bank itself: every `## Qnn-nnn question` heading starts a passage that holds the question and its whole answer, including code blocks (headings inside fenced code are ignored). The `Category: … | Priority: …` line becomes metadata, together with `id`, `title`, `question`, `bank` and `filename`; the embedded text is `Question: …` followed by the answer. Spring AI's `MarkdownDocumentReader` is not used because it turns each code block and block quote into a separate passage without a title. No token-based splitter is applied: the longest answer is about 2,400 characters, well within the embedding model's input limit. A read failure raises an error naming the resource pattern; an empty result is logged as a warning.

`KnowledgeBaseVectorStoreConfig` creates the store through `PersistentVectorIndex`, using the `dashscopeEmbeddingModel` bean. The bean and its injection point in `KnowledgeAssistant` are `@Lazy`, so nothing is loaded or embedded until the first RAG request; ordinary chat never touches the vector store.

`PersistentVectorIndex` computes a SHA-256 fingerprint over the embedding model name and every passage's file, title, and text (order-independent). If a saved index with the same fingerprint exists, it is loaded without calling the embedding API; otherwise every passage is embedded, in batches of 10 (the DashScope limit per request), and the index is saved. The index file is written before the fingerprint file, each through a temporary file and a move, so an interrupted save is treated as a mismatch next time. A save failure is logged and the in-memory index is still used.

## Persistence

`MessageWindowChatMemory` keeps at most the configured number of recent messages. `FileBasedChatMemoryRepository` serializes the current list with Kryo into one file per conversation. The ID is encoded for a safe filename; writes use a temporary file and replacement. The memory window is context for the model, not a complete audit history.

`SynchronizedChatMemory` protects individual memory operations. `ConversationExecutor` covers the larger read → model call → write sequence for a conversation. Both operate within one JVM; neither is a distributed lock.

Uploaded images are stored separately and referenced by generated IDs in user-message metadata. A later turn can rehydrate retained images. If a referenced file disappears, the request fails instead of silently dropping the image.

## Design decisions

| Decision | Reason | Trade-off |
| --- | --- | --- |
| Manual `memory.get()` / `memory.add()` instead of `MessageChatMemoryAdvisor` | A turn is saved only after the answer (or report) is validated, and RAG must save the original question rather than the grounded prompt | More orchestration code in `KnowledgeAssistant` |
| Retrieved text kept out of memory | Prevents reference material from filling the bounded window and being re-sent on every later turn | Follow-up questions do not see earlier passages unless retrieval finds them again |
| Images sent directly to a multimodal model | The use case is understanding screenshots and photos in conversation, not extracting precise layout or coordinates | No separate detection/OCR stage, so accuracy depends on the chosen model; text-only routes reject image conversations |
| Explicit model routes with declared input types, no silent fallback | A request never reaches a model that cannot read its inputs | Capability flags are configuration, not a live provider query |
| Local index built lazily from classpath files and saved to disk | No external database to run for a portfolio demo; restarts skip re-embedding | The first request after a document change is slow; one JSON file per instance, not shared |
| No-match questions still reach the model, without passages | A follow-up such as "what did I tell you earlier?" retrieves nothing but should still be answered from history | One model call even when the knowledge base cannot help |
| Blocklist checks the user's words, not retrieved passages | Knowledge-base text (for example a warning about fraud) must not reject a valid question | Passages themselves are not screened |
| Metrics through an Advisor and Micrometer, without message text | Latency, outcome, and token usage per operation and model, with no conversation content in metrics | Model tag depends on what the provider reports |
| Similarity threshold defaults to 0 | A useful value depends on the embedding model and question language; the evaluation report measures it | No filtering until a threshold is chosen |
| Grounded prompt tells the model to treat material as information | Reduces the chance that instructions inside documents steer the model | A prompt instruction, not an enforced guarantee |

## Scope and trade-offs

- The backend has no authentication or tenant isolation. Conversation and attachment IDs must not be treated as authorization tokens.
- Local files and locks suit a single instance. Multi-instance deployment requires shared storage and cross-instance coordination.
- The example blocklist is deterministic substring matching. It can reject harmless uses of a word and does not analyze image content.
- Attachment expiry and disk-wide quotas are not implemented.
- MySQL code is an optional prototype. The running application does not use MySQL.
- The evaluation set measures retrieval only (hit rate, rank, scores). Answer quality is still checked by the live conversation tests and human review of their transcripts.
- The saved index is a local file; several instances would each build their own. A shared vector database would replace it for multi-instance deployment.

## Testing

`./mvnw clean test` runs the offline suite. Tests cover upload formats and limits, model capability checks, remembered images, failed-call memory behavior, structured reports, concurrent requests, HTTP errors, and Google SDK requests against a local stub.

RAG and metrics tests run offline with mocks or a deterministic fake embedding model:

| Test | Checks |
| --- | --- |
| `KnowledgeAssistantRagTest` | Retrieved text reaches the model but only the question is stored; sources are returned; top-k and threshold reach the search; no-match prompt; blocklist ignores passages but rejects blocked questions before retrieval; failures leave memory unchanged; retrieval metrics |
| `KnowledgeAssistantRagCloudTest` | Cloud retrieval augments the prompt, returns sources, leaves memory unchanged on failure, and reports `MODEL_NOT_AVAILABLE` when disabled |
| `KnowledgeAssistantMetricsTest` | Call timer and token counters; blocked requests recorded with their error code |
| `RagApiTest` | `/assistant/rag` JSON including `sources`; blocked question returns `CONTENT_NOT_ALLOWED` |
| `PersistentVectorIndexTest` | Saved index reused without re-embedding; rebuild on document change, model change, or corrupt file |
| `RagEvaluatorTest` | Hit rate, mean reciprocal rank, suggested threshold, entry-ID parsing |
| `KnowledgeBaseLoaderTest` | One passage per question with code and metadata kept; all 1,233 bundled questions load with unique IDs and no image links |

Live tests require `RUN_LIVE_AI_TESTS=true` and valid credentials. `KnowledgeAssistantRagLiveTest` checks a grounded, cited answer, memory across three turns, isolation between conversations, and refusal to invent an answer, writing transcripts under `target/rag-live-tests`. `RagRetrievalEvaluationLiveTest` runs the labelled question set against the real embedding model, writes `target/rag-eval/report.md`, and fails if the hit rate falls below 70%.
