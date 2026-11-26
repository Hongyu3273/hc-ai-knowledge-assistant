# Configuration and model routing

## Local setup

Copy `src/main/resources/application-local.yml.example` to `src/main/resources/application-local.yml`. The local file is ignored by Git. Keep real API keys out of source control.

The shared defaults are in `src/main/resources/application.yml`. The default Spring profile is `local`; use `SPRING_PROFILES_ACTIVE` to choose another profile.

### DashScope

Set `AI_DASHSCOPE_API_KEY` and leave `app.assistant.model` as `dashscope`. The configured DashScope model is `qwen-plus`. The legacy Spring AI and LangChain4j example endpoints also use DashScope.

### Gemini

Set `GEMINI_API_KEY` and enable the Gemini client:

```yaml
app:
  gemini:
    enabled: true
    api-key: ${GEMINI_API_KEY:}
  assistant:
    model: gemini-2.5-pro
```

The registered route IDs are `gemini-2.5-pro` and `gemini-2.5-flash-lite`. They share a Google SDK client. Their underlying provider model IDs can be changed with `app.gemini.pro-model` and `app.gemini.flash-lite-model`; verify account access and input capabilities before changing them. The Gemini client is created only when `app.gemini.enabled=true`.

The business default can also be selected with `ASSISTANT_MODEL` in the shared configuration. Explicit values in the local YAML take precedence over placeholders in the shared file.

### Other settings

| Property or environment variable | Default | Purpose |
| --- | --- | --- |
| `CHAT_MEMORY_DIR` | `${user.dir}/tmp/chat-memory` | Local conversation files |
| `CHAT_MEMORY_MAX_MESSAGES` | `10` | Retained message window |
| `CHAT_ATTACHMENT_DIR` | `${user.dir}/tmp/chat-attachments` | Uploaded image files |
| `app.attachments.max-file-bytes` | `10485760` | Per-image byte limit |
| `app.attachments.max-pixels` | `16000000` | Per-image pixel limit |
| `app.attachments.max-context-bytes` | `31457280` | Total image bytes in effective context |

The local memory and attachment directories are ignored by Git. Back them up together if conversations must survive a machine move.

## Model routing design

`AssistantModelConfiguration` registers provider-backed `ModelRoute` entries. `ModelCatalog` resolves the requested ID or the configured default and exposes input capabilities to the API. `KnowledgeAssistant` builds business `ChatClient` instances with the system prompt and Advisor, then applies a per-request model override.

An unknown or disabled model produces `MODEL_NOT_AVAILABLE`; there is no silent fallback. The older `/api/ai/chat` and LangChain4j endpoints remain educational examples and are separate from the `/api/assistant/*` workflow.

## Retrieval (RAG)

All settings are under `app.assistant.rag` in `application.yml`.

| Property | Environment variable | Default | Purpose |
| --- | --- | --- | --- |
| `top-k` | | `4` | Maximum passages sent to the model per question |
| `similarity-threshold` | `RAG_SIMILARITY_THRESHOLD` | `0.0` | Minimum similarity (0 to 1); `0` accepts every passage |
| `source-excerpt-chars` | | `200` | Length of each `sources[].excerpt` in the response |
| `index.persist` | | `true` | Save the embedded index and reuse it on later starts |
| `index.directory` | `RAG_INDEX_DIR` | `${user.dir}/tmp/rag-index` | Where the index is saved (ignored by Git) |
| `cloud.enabled` | `RAG_CLOUD_ENABLED` | `false` | Create the DashScope cloud retriever and expose `POST /assistant/rag/cloud` |
| `cloud.index-name` | `RAG_CLOUD_INDEX_NAME` | `Interview Knowledge Base` | DashScope knowledge-base index name |

Other inputs:

- **Documents:** every `src/main/resources/document/*.md` file on the classpath. Each `## Qnn-nnn` question and its answer become one passage. These question banks are ignored by Git and must be supplied by you; see "Knowledge base" in the [README](../README.md#knowledge-base) for the format. With no banks the application still starts, and RAG answers say that no relevant material was found.
- **Embeddings:** the `dashscopeEmbeddingModel` bean from the Spring AI Alibaba DashScope starter, used even when the default chat route is Gemini or Ollama. The DashScope key must be valid for RAG.
- **Chat model:** the default route (`app.assistant.model`). RAG requests cannot select another route.

### Choosing a similarity threshold

The threshold ships at `0` because a good value depends on the embedding model and the language of the questions; English questions against these English documents and cross-language questions score differently. To choose one:

1. Run the retrieval evaluation (needs the DashScope key; no chat calls): `RUN_LIVE_AI_TESTS=true ./mvnw test -Dtest=RagRetrievalEvaluationLiveTest`. In Windows PowerShell: `$env:RUN_LIVE_AI_TESTS='true'; .\mvnw.cmd test -Dtest=RagRetrievalEvaluationLiveTest`.
2. Open `target/rag-eval/report.md`. It lists, per question, the expected question IDs, the retrieved IDs, and the top score, and gives a suggested threshold when answerable and out-of-scope questions separate cleanly.
3. Set `RAG_SIMILARITY_THRESHOLD` to that value (or slightly lower), rerun, and check that the hit rate did not drop.

The question set is `src/test/resources/rag-eval/eval-set.json`: each case has an `id`, a `question`, and the `expected` question IDs such as `Q03-305` (empty for questions the knowledge base cannot answer). The file is ignored by Git because its cases are labelled with IDs from one person's question banks; write your own for your banks (the format is shown in the README), and add cases when you add documents. Without the file the evaluation test is skipped.

### Saved index

When `index.persist` is true, the first RAG request writes `knowledge-index.json` and `knowledge-index.sha256` to `index.directory`. The `.sha256` file holds a fingerprint of every passage (file, title, text) and the embedding model name (`spring.ai.dashscope.embedding.options.model`, or `provider-default` when unset). On the next start the saved index is loaded only if the fingerprint matches; editing a document or changing that property triggers a rebuild. A missing or corrupt file also triggers a rebuild. To force one, delete the directory.

### Cloud retrieval

Set `RAG_CLOUD_ENABLED=true` and, if needed, `RAG_CLOUD_INDEX_NAME`. Create the index and upload documents in the DashScope (Alibaba Cloud Model Studio) console first; the name must match exactly. The bundled Markdown files are not uploaded automatically. When disabled, the Advisor bean is not created and `/assistant/rag/cloud` does not exist.

## Metrics and logging

Spring Boot Actuator exposes `health` and `metrics` under `/api/actuator`. See the [API guide](api.md#health-and-metrics) for the `hc.ai.*` meters. To export to a monitoring system, add the matching Micrometer registry (for example `micrometer-registry-prometheus`) and include its endpoint in `management.endpoints.web.exposure.include`.

`app.assistant.log-advisor.enabled` (`ASSISTANT_LOG_ADVISOR_ENABLED`, default `false`) registers `MyLoggerAdvisor` on business clients. It logs user text and model answers, so keep it off outside local debugging.

## Storage option

`MySqlChatMemoryRepository` and `src/main/resources/chat-memory-mysql.sql` illustrate a MySQL-backed repository. They are not wired into the application and have not been validated against a live MySQL server. The active repository is `FileBasedChatMemoryRepository`.
