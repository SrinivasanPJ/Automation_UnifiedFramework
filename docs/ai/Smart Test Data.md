# Smart Test Data (Agentic AI) — Demo Brief

````md
## Objective

Showcase reliable, policy-safe, and zero-block test data generation that works with or without an LLM. The feature
produces strict JSON user profiles for registration scenarios and automatically falls back to deterministic fakes when a
model or key is unavailable, so pipelines stay green.

## What It Does

- **Strict JSON contract** produced by `DataGenAgent`:
  ```json
  { "firstName": "...", "lastName": "...", "email": "...", "phone": "...", "address": "..." }
````

* **US-localized profile** (names, email, 555-010- style phone, full mailing address).
* **Robust parsing & normalization**
  Extracts JSON from any LLM response, normalizes common key variants (`first_name`, `emailAddress`, etc.).
* **Validation with auto-fallback**
  If any required field is blank/invalid → deterministic fake profile (e.g., `alex.miller+<ts>@example.com`) to ensure
  repeatability and no pipeline blocking.
* **PII-safe observability**
  Extent/JIRA logs show masked profile and a source tag:

    * *AI Data Source: LLM* **or**
    * *AI Data Source: Fallback (no/invalid model)*

## Architecture & Flow (High Level)

1. Caller (e.g., `RegisterAiDataDemoTest`) requests a profile: `new DataGenAgent(llm).userProfile()`.
2. **LLM path (if configured)**
   LlmClient → prompt → JSON text → extract & normalize → validate → return.
3. **Fallback path (no key/endpoint/invalid JSON)**
   Generate deterministic profile (seeded by timestamp for uniqueness) → return immediately (no network).
4. Caller uses the profile to fill registration and logs the masked summary + source.

## Run the Demo

### LLM path

```bash
set OPENAI_API_KEY=YOUR_KEY
set LLM_MODEL=llama-3.1-8b-instant
set OPENAI_ENDPOINT=https://api.groq.com/openai/v1/chat/completions
mvn -q -Dtest=RegisterAiDataDemoTest test
```

**You’ll see in console/Extent:**

* `AI Data Source: LLM`
* `AI Profile (masked): {firstName=…, lastName=…, email=a***@domain.com, phone=***…45, address=…}`

### Fallback path (prove zero blocking)

```bash
set OPENAI_API_KEY=
mvn -q -Dtest=RegisterAiDataDemoTest test
```

**You’ll see:**

* `AI Data Source: Fallback (no/invalid model)`
* Email like `alex.miller+<ts>@example.com`. Test still passes.

### Optional: drive the CLI

```bash
mvn -q -DskipTests exec:java \
  -Dexec.mainClass=com.MyridiusUAF.cli.AiGenerateTest \
  "-Dexec.args=As a new user I can register with smart AI data RegisterAiDataDemoTest com.MyridiusUAF.tests.ai"
```

> Note: Authoring prompt doesn’t insert `DataGenAgent` usage, so the manual class is the quickest, reliable demo.

## Contract & Validation

* **Required:** `firstName`, `lastName`, `email` (must contain `@`).
* **Optional (generated):** `phone` (`555-010-xxxx`), `address` (street, city, state ZIP).
* **Normalization:** accepts `first_name`, `emailAddress`, `phone_number`, etc.
* **Error handling:** any parse/validation issue → fallback profile.

## Observability (What to Show)

* **Extent Report entries:**

    * `AI Data Source: LLM` or `Fallback (no/invalid model)`
    * `AI Profile (masked): {firstName=…, lastName=…, email=a***@…, phone=***…45, address=…}`
* **Optional JIRA ADF comment** includes the same masked summary and attached screenshot.

## CI/CD & Policy

* No hard dependency on LLM at runtime; safe fallback ensures stability.
* Deterministic fakes → reproducible and audit-friendly.
* PII-safe: only synthetic data; masking in all logs/reports.
* Config via env vars (`OPENAI_API_KEY`, `LLM_MODEL`, `OPENAI_ENDPOINT`)—easy to toggle per environment.

## Success Criteria (for the pilot)

* 100% non-blocking runs with or without LLM.
* ≤ 1% data-format failures (guarded by validation).
* Reduced flaky reruns due to missing/invalid registration data.

## One-Slide Talking Points

* “Smart Test Data: strict JSON when AI is available; deterministic fakes when it’s not.”
* “Zero pipeline risk, reproducible profiles, and PII-safe reporting.”
* “Drop-in for existing tests, no framework rewrites.”

## Output: (Example)

```
2025-09-26 08:24:35 [main] INFO  ... RegisterAiDataDemoTest - AI Data Source: LLM
2025-09-26 08:24:35 [main] INFO  ... RegisterAiDataDemoTest - AI Profile (masked):
{firstName=Julian, lastName=Foster, email=j***@outlook.com, phone=**********34,
address=1234 Oak St, Apt 101, Los Angeles, CA 90012}
```

```
::contentReference[oaicite:1]{index=1}
```
