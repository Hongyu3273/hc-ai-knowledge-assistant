# API and behavior

All paths below use the default base URL `http://localhost:8123/api`. The service is a backend demonstration with no login system or frontend.

## Available models

`GET /api/ai/models` returns the default model and registered routes with their declared input capabilities:

```json
{
  "defaultModel": "dashscope",
  "models": [
    {"id": "dashscope", "supportedInputs": ["text"]},
    {"id": "gemini-2.5-pro", "supportedInputs": ["text", "image"]},
    {"id": "gemini-2.5-flash-lite", "supportedInputs": ["text", "image"]}
  ]
}
```

The actual list depends on configuration. Capability flags are application configuration, not a live provider query.

## Upload an image

```sh
curl http://localhost:8123/api/attachments -F 'file=@/absolute/path/example.png'
```

The endpoint returns HTTP 201 and a generated `fileId`, MIME type, size, width, and height. Use that ID in chat requests; file system paths are never accepted as attachment references.

Supported formats: JPEG, PNG, and WebP. The service checks file content rather than trusting the extension or uploaded MIME type. Defaults are 10 MiB per file, 16 million pixels, one attachment per message, and 30 MiB of effective image context. Corrupt images are rejected.

## Chat

```sh
curl http://localhost:8123/api/assistant/chat \
  -H 'Content-Type: application/json' \
  -d '{
    "chatId": "demo-1",
    "model": "gemini-2.5-pro",
    "text": "What should I say next?",
    "attachments": [{"fileId": "replace-with-uploaded-id"}]
  }'
```

Response:

```json
{"chatId": "demo-1", "model": "gemini-2.5-pro", "content": "Model response"}
```

`model` may be omitted to use the configured default. `chatId` is required and limited to 128 UTF-8 bytes. Text is limited to 20,000 characters. Text and attachments cannot both be empty. An image-only request receives a default question. A later request with the same `chatId` can refer to an image already in the retained memory window without uploading it again.

If the active conversation contains images, switching to a text-only model is rejected. Switching providers sends the retained context to the newly selected provider.

## Structured report

`POST /api/assistant/report` accepts the chat request fields plus optional `userName` and `focusArea` (for example `Java backend`). It returns a report with `title` and `suggestions`:

```json
{
  "chatId": "demo-1",
  "model": "gemini-2.5-pro",
  "report": {
    "title": "Communication plan",
    "suggestions": ["State your concern clearly", "Ask an open question"]
  }
}
```

The report is committed to conversation memory only after the model response is parsed and validated.

## Knowledge-base chat (RAG)

`POST /api/assistant/rag` answers a text question using questions retrieved from your interview question banks (`src/main/resources/document/*.md`, not in Git; see the README for the format). The model is asked to cite the question IDs it relies on, such as `[Q03-305]`.

```sh
curl http://localhost:8123/api/assistant/rag \
  -H 'Content-Type: application/json' \
  -d '{"chatId": "demo-2", "message": "Why is my @Transactional method not rolling back?"}'
```

Response:

```json
{
  "chatId": "demo-2",
  "model": "dashscope",
  "content": "Model response",
  "sources": [
    {
      "id": "Q03-305",
      "bank": "03 Java Backend",
      "category": "Spring",
      "filename": "03-java-backend.md",
      "title": "Q03-305 When does @Transactional not take effect?",
      "score": 0.81,
      "excerpt": "First 200 characters of the passage…"
    }
  ]
}
```

`sources` lists the passages that were supplied to the model, best match first. `score` is the similarity from 0 to 1 (absent if the retriever does not report one); `excerpt` is the first 200 characters of the answer (`app.assistant.rag.source-excerpt-chars`). An empty list means no passage passed the similarity threshold.

| Field | Rules |
| --- | --- |
| `chatId` | Required, not blank, at most 128 characters (the shared chat validation also applies its 128 UTF-8 byte limit) |
| `message` | Required, not blank, at most 20,000 characters |

Behavior:

- The request has no `model` or `attachments` field. It always uses the configured default route, which the response reports in `model`.
- Up to `app.assistant.rag.top-k` passages (default 4) with a similarity at or above `app.assistant.rag.similarity-threshold` (default 0, accept all) are sent to the model with the question for this call only. Memory stores the original question and the answer, not the retrieved text.
- If no passage passes the threshold, the model is still called, with the conversation history and an instruction that the knowledge base had nothing relevant. It can then answer from earlier messages or say that it found nothing, instead of being given unrelated passages.
- The blocklist checks only the user's `message`, before retrieval. Words inside knowledge-base passages do not cause `CONTENT_NOT_ALLOWED`.
- The same `chatId` can be used with `/assistant/chat` and `/assistant/rag`; both read and write the same conversation memory.
- The first RAG request after startup builds the index. With index persistence on (the default), later starts load the saved index instead of re-embedding, unless the documents or embedding model changed.
- Embeddings use DashScope, so `AI_DASHSCOPE_API_KEY` is required even when the default chat route is Gemini.

## Cloud knowledge-base chat (optional)

`POST /api/assistant/rag/cloud` takes the same request and returns the same response shape, but retrieves from a DashScope knowledge-base index through Spring AI's `RetrievalAugmentationAdvisor`. The endpoint exists only when `app.assistant.rag.cloud.enabled=true` (environment variable `RAG_CLOUD_ENABLED=true`); otherwise it returns 404. Create and populate the index in the DashScope console first; see [configuration](configuration.md#retrieval-rag).

## Health and metrics

Spring Boot Actuator exposes `GET /api/actuator/health` and `GET /api/actuator/metrics`. Application meters, which never include message text:

| Meter | Type | Tags | Meaning |
| --- | --- | --- | --- |
| `hc.ai.model.calls` | timer | `operation` (chat, report, rag, rag_cloud), `model`, `outcome` (success, an error code, or error) | Latency and count of model calls; blocked chat and report requests appear with outcome `CONTENT_NOT_ALLOWED` (RAG questions are rejected before this point) |
| `hc.ai.model.tokens` | counter | `operation`, `model`, `type` (prompt, completion) | Tokens reported by the provider |
| `hc.ai.rag.retrieval` | timer | `mode` (local) | Vector search time, including the query embedding |
| `hc.ai.rag.retrieved.documents` | summary | `mode` (local, cloud) | Passages supplied per question |
| `hc.ai.rag.no.match` | counter | `mode` | Questions where no passage passed the threshold |

Example: `GET /api/actuator/metrics/hc.ai.model.calls?tag=operation:rag`.

## Error response

```json
{"code": "MODEL_INPUT_NOT_SUPPORTED", "message": "Explanation", "errorId": "generated-id"}
```

| HTTP status | Codes | Meaning |
| --- | --- | --- |
| 400 | `INVALID_REQUEST`, `EMPTY_CHAT_INPUT` | Invalid or empty input |
| 400 | `MODEL_NOT_AVAILABLE`, `MODEL_INPUT_NOT_SUPPORTED` | Missing model, unsupported input, or cloud retrieval called while disabled |
| 400 | `UNSUPPORTED_ATTACHMENT_TYPE`, `INVALID_ATTACHMENT`, `CONTENT_NOT_ALLOWED` | Invalid attachment or blocked text |
| 404 | `ATTACHMENT_UNAVAILABLE` | Referenced file cannot be loaded |
| 413 | `ATTACHMENT_TOO_LARGE` | File, image, or effective context limit exceeded |
| 502 | `MODEL_CALL_FAILED` | Provider call, report parsing, or (for RAG) retrieval/embedding failed |
| 500 | `STORAGE_FAILURE`, `INTERNAL_ERROR` | Local storage or internal failure |

The application returns safe error messages rather than raw provider errors, API keys, or local storage paths. The example blocklist checks text only; it is not image content moderation.

The legacy example endpoints (`/api/ai/chat` and the LangChain4j controller) are educational samples outside the `/api/assistant/*` workflow and are not covered by this guide.
