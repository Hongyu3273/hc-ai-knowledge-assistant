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
curl http://localhost:8123/api/love/chat \
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

`POST /api/love/report` accepts the chat request fields plus optional `userName` and `relationshipStatus`. It returns a report with `title` and `suggestions`:

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

## Error response

```json
{"code": "MODEL_INPUT_NOT_SUPPORTED", "message": "Explanation", "errorId": "generated-id"}
```

| HTTP status | Codes | Meaning |
| --- | --- | --- |
| 400 | `INVALID_REQUEST`, `EMPTY_CHAT_INPUT` | Invalid or empty input |
| 400 | `MODEL_NOT_AVAILABLE`, `MODEL_INPUT_NOT_SUPPORTED` | Missing model or unsupported input |
| 400 | `UNSUPPORTED_ATTACHMENT_TYPE`, `INVALID_ATTACHMENT`, `CONTENT_NOT_ALLOWED` | Invalid attachment or blocked text |
| 404 | `ATTACHMENT_UNAVAILABLE` | Referenced file cannot be loaded |
| 413 | `ATTACHMENT_TOO_LARGE` | File, image, or effective context limit exceeded |
| 502 | `MODEL_CALL_FAILED` | Provider call or report parsing failed |
| 500 | `STORAGE_FAILURE`, `INTERNAL_ERROR` | Local storage or internal failure |

The application returns safe error messages rather than raw provider errors, API keys, or local storage paths. The example blocklist checks text only; it is not image content moderation.
