# Configuration and model routing

## Local setup

Copy `src/main/resources/application-local.yml.example` to `src/main/resources/application-local.yml`. The local file is ignored by Git. Keep real API keys out of source control.

The shared defaults are in `src/main/resources/application.yml`. The default Spring profile is `local`; use `SPRING_PROFILES_ACTIVE` to choose another profile.

### DashScope

Set `AI_DASHSCOPE_API_KEY` and leave `app.love.model` as `dashscope`. The configured DashScope model is `qwen-plus`. The legacy Spring AI and LangChain4j example endpoints also use DashScope.

### Gemini

Set `GEMINI_API_KEY` and enable the Gemini client:

```yaml
app:
  gemini:
    enabled: true
    api-key: ${GEMINI_API_KEY:}
  love:
    model: gemini-2.5-pro
```

The registered route IDs are `gemini-2.5-pro` and `gemini-2.5-flash-lite`. They share a Google SDK client. Their underlying provider model IDs can be changed with `app.gemini.pro-model` and `app.gemini.flash-lite-model`; verify account access and input capabilities before changing them. The Gemini client is created only when `app.gemini.enabled=true`.

The business default can also be selected with `LOVE_APP_MODEL` in the shared configuration. Explicit values in the local YAML take precedence over placeholders in the shared file.

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

`LoveModelConfiguration` registers provider-backed `ModelRoute` entries. `ModelCatalog` resolves the requested ID or the configured default and exposes input capabilities to the API. `LoveApp` builds business `ChatClient` instances with the system prompt and Advisor, then applies a per-request model override.

An unknown or disabled model produces `MODEL_NOT_AVAILABLE`; there is no silent fallback. The older `/api/ai/chat` and LangChain4j endpoints remain educational examples and are separate from the `/api/love/*` workflow.

## Storage option

`MySqlChatMemoryRepository` and `src/main/resources/chat-memory-mysql.sql` illustrate a MySQL-backed repository. They are not wired into the application and have not been validated against a live MySQL server. The active repository is `FileBasedChatMemoryRepository`.
