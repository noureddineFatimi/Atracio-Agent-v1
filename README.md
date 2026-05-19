# Atracio Agent Service

Standalone Spring Boot service that exposes a conversational AI agent over the Atracio ERP.
The agent accepts natural language messages, selects the appropriate tool, calls the Atracio
HTTP API with the user's bearer token, and returns a structured reply.

---

## Architecture

```
User (console / HTTP)
        │
        ▼
  ChatController  ──────────────────────────────────────────────────────
        │                                                               │
        ▼                                                               │
 AgentOrchestrator                                                      │
        │                                                               │
        ├── SystemPromptFactory      (builds the LLM system prompt)     │
        ├── ConversationService      (in-memory history per session)    │
        ├── ToolDefinitionRegistry   (7 tool schemas sent to the LLM)   │
        │                                                               │
        ├── LlmProvider (Gemini | Ollama │ OpenAI)                      │
        │                                                               │
        └── ToolDispatcher ──► ToolExecutor (7 tools)                   │
                                      │                                 │
                                      ▼                                 │
                            AtracioBackendClient                        │
                            (Mock | Http)                               │
                                      │                                 │
                                      ▼                                 │
                            demo.prod.atracio.com ───────────────────── 
```

**Rules enforced by design:**
- The agent never touches the Atracio database directly — HTTP only
- The user's bearer token is forwarded as-is to every backend call — never stored
- Business validation stays in Atracio — the agent never reimplements rules
- The LLM never sees raw Atracio responses — only normalised `ToolResponse` envelopes

---

## Prerequisites

| Tool | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ |
| Gemini API key | [aistudio.google.com](https://aistudio.google.com) |
| Hugging Face API key | [huggingface.co/](https://huggingface.co/docs/inference-providers/index) |
| Atracio tenant | `https://demo.prod.atracio.com` |

---

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `GEMINI_API_KEY` | ✅ | — | Google Gemini API key |
| `HUGGING_FACE_API_KEY` | ✅ | — | Hugging Face API key |
| `GEMINI_MODEL` | ❌ | `gemini-2.5-flash` | Gemini model name |
| `OLLAMA_MODEL` | ❌ | `qwen2.5:1.5b` | Ollama model name |
| `OPENAI_MODEL` | ❌ | `gpt-oss-120b` | OpenAI model name |
| `ATRACIO_BASE_URL` | ✅ | - | Atracio tenant base URL |
| `OLLAMA_BASE_URL` | ❌ | `http://localhost:11434` | Ollama base url |
| `HUGGING_FACE_BASE_URL` | ❌ | `https://router.huggingface.co` | hugging base url |

---

## Run Locally

```bash
# Clone and build
git clone https://github.com/noureddineFatimi/Atracio-Agent-v1
cd agent

# Run with mock backend (no real Atracio needed)
GEMINI_API_KEY=your-key mvn spring-boot:run

# Run with real Atracio backend
GEMINI_API_KEY=your-key \
SPRING_PROFILES_ACTIVE=http,gemini,console-runner \
mvn spring-boot:run
```

The server starts on port `8080`. The console runner starts automatically in the same process.

---

## Console Runner

The console runner starts automatically alongside the web server.
It is the primary tool for local testing.

```
╔══════════════════════════════════════════╗
║   Atracio Agent — Console Runner         ║
╚══════════════════════════════════════════╝

Atracio Bearer Token: eyJ...

Session started. conversationId=console-a1b2c3d4
Type 'reset' to clear history, 'exit' to quit.

You: Show me draft sales orders for ACME
Tool    : ✓ document.search
Agent   : I found 2 draft sales orders for ACME Corp:
          • SO-000101 — DRAFT — created 2026-03-01
          • SO-000102 — DRAFT — created 2026-03-15
          Would you like to see the details of one of these orders?

You: release order 101
Tool    : ✓ document.apply_process_action
Agent   : Sales order SO-000101 has been released successfully.
          Its status is now RELEASED.

You: reset
[reset] New conversation started. id=console-b5c6d7e8

You: exit
Console runner stopped.
```

**Commands:**

| Input | Effect |
|---|---|
| Any text | Sends the message to the agent |
| `reset` | Starts a new conversation (clears history) |
| `exit` / `quit` | Stops the console runner (server keeps running) |

---

## Call POST /chat

```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{
    "userMessage":    "Show me the latest purchase orders for Global Vendor",
    "conversationId": "my-session-001",
    "tenant":         "demo",
    "bearerToken":    "eyJ..."
  }'
```

**Response:**

```json
{
  "assistantMessage": "I found 3 purchase orders for Global Vendor...",
  "conversationId":   "my-session-001",
  "toolCalls":        [{"tool"  : "document.search", "status": "success"}]
}
```

**Reset a conversation:**

```bash
curl -X DELETE http://localhost:8080/chat/my-session-001
# 204 No Content
```

---

## Health Check

```bash
curl http://localhost:8080/actuator/health
# { "status": "UP" }
```

---

## Available Tools

| Tool | Description |
|---|---|
| `document.search` | List and filter documents (SalesOrder, PurchaseOrder, etc.) |
| `document.get_details` | Retrieve the full payload of a single document |
| `document.save_draft` | Create or modify a DRAFT document |
| `document.apply_process_action` | Release, approve, post, cancel a document |
| `wms.get_article_stock_summary` | Consolidated stock view for an article |
| `wms.lookup_inventory_unit` | Find a unit by barcode, RFID tag, or serial number |
| `partner.get_summary` | Commercial summary for a client or vendor |

**Supported process actions:**

| Family | Actions |
|---|---|
| `lifecycle` | `release`, `close`, `cancel` |
| `approval` | `submit`, `approve` |
| `posting` | `post`, `reverse` |
| `execution` | `start`, `complete` |
| `payment` | `allocate` |

---

## Enable/Disable console runner

**Console runner enabled:**

```yaml
# application.yml
spring:
  profiles:
    active: mock,gemini,console-runner
```

**Console runner disabled (Only the server is running):**

```yaml
# application.yml
spring:
  profiles:
    active: mock,gemini
```

---

## Switch LLM Provider

**Gemini (default):**

```yaml
# application.yml
spring:
  profiles:
    active: mock,gemini
  ai:
    google:
      gemini:
        api-key: ${GEMINI_API_KEY}
        chat:
          options:
            model: ${GEMINI_MODEL:gemini-2.0-flash}
            temperature: 0.2
```

**Ollama (local, no API key):**

```bash
SPRING_PROFILES_ACTIVE=mock,ollama \
OLLAMA_BASE_URL=http://localhost:11434 \
OLLAMA_MODEL=llama3.2 \
mvn spring-boot:run
```

---

## Switch Backend

| Profile | Backend | Use case |
|---|---|---|
| `mock` | Static responses | Local dev, CI, all unit tests |
| `http` | Real `demo.prod.atracio.com` | Integration testing, production |

```bash
# Mock (default)
SPRING_PROFILES_ACTIVE=mock,gemini mvn spring-boot:run

# Real backend
SPRING_PROFILES_ACTIVE=http,gemini mvn spring-boot:run
```

---

## Run Tests

```bash
# All unit and mock-stack integration tests (no external dependencies)
mvn test

# Real backend integration tests (requires a valid Atracio token)
SPRING_PROFILES_ACTIVE=http,gemini \
ATRACIO_TEST_TOKEN=eyJ... \
mvn test -Dtest=RealBackendIntegrationTest
```

**Test suites:**

| Suite | Profile | External deps | Description |
|---|---|---|---|
| Unit tests (`*Test`) | none | none | Fast, isolated, always green |
| `MockStackIntegrationTest` | `mock` | none | Full Spring context, mock backend |

---

## Package Structure

```
src/main/java/atracio/agent/
├── AgentServiceApplication.java
├── ConsoleRunner.java
├── agent/
│   ├── AgentOrchestrator.java
│   ├── ConversationService.java
│   └── SystemPromptFactory.java
├── atracio/
│   ├── AtracioBackendClient.java         (interface)
│   ├── AtracioBackendClientMock.java     (profile: mock)
│   ├── AtracioBackendClientHttp.java     (profile: http)
│   ├── AtracioBackendException.java
│   ├── AtracioErrorMapper.java
│   └── AtracioUrlResolver.java
├── config/
│   ├── HttpClientConfig.java
│   ├── GenAiChatClientConfig
│   ├── OllamaChatClientConfig
│   └── OpenAiChatClientConfig        
├── controller/
│   ├── ChatController.java
│   └── HealthController.java
├── dto/
│   ├── ChatRequest.java
│   ├── ToolCallDto.java
│   └── ChatResponse.java
├── provider/
│   ├── LlmProvider.java                  (interface)
│   ├── GeminiChatProvider.java           (profile: gemini)
│   ├── OpenAIChatProvider.java           (profile: openai)
│   └── OllamaChatProvider.java           (profile: ollama)
└── tools/
    ├── ToolDefinitionRegistry.java
    ├── ToolDispatcher.java
    ├── ToolExecutor.java
    ├── ToolShemas.java
    ├── ToolResponse.java
    └── ToolResponse.java
```

---

## Key Design Decisions

**No Atracio code imported.** The agent is fully standalone — it communicates with Atracio exclusively over HTTP.

**Bearer token always explicit.** Every method that calls Atracio receives the token as a parameter. It is never stored in a bean, never cached, never shared between users.

**Normalised error envelope.** Every tool returns `ToolResponse { ok, tool, data, error, meta }`. The LLM never sees a raw Atracio error.

**Aggregation with partial resilience.** `wms.get_article_stock_summary` and `partner.get_summary` make multiple sub-calls. If one fails, the field is `null` in the result — the other fields are still returned.

**Two LLM calls per tool turn.** The first call lets the LLM decide which tool to use. After execution, the second call produces the natural language reply from the tool result.