# AI Triage & Reporting – Quick Demo Guide

```markdown
## 1) Goal

When a UI test fails, produce a concise, 4-section triage (Summary, Root Cause, Evidence, Fix Suggestion) and push it to
**Console**, **Extent report**, a **Markdown artifact**, and **Jira attachments** without blocking the pipeline (LLM
optional).

---

## 2) What We Implemented

### Listener pair

- **AiTriageListener (on failure):**
    - Collects stack + metadata + optional screenshot.
    - Prompts the LLM (or fallback rules).
    - Writes `reports/triage/<test>_<ts>.md`.
    - Logs to Extent.
    - Signals readiness via a shared `CountDownLatch` in the TestNG context.

- **JiraListener (on failure):**
    - Waits briefly for the latch.
    - Attaches the triage `.md` (plus report/screenshot).
    - Posts an ADF comment.

### Resilience

- If `OPENAI_API_KEY` is absent, a **rule-based fallback** still produces a professional triage. No pipeline blocking.

### Idempotent attachments

- Short poll + disk fallback ensure we don’t miss the `.md` even with listener ordering differences.

**Config switches (e.g., in `config.properties` or env-backed):**
```

- ai.triage.enabled=true
- jira.ai.triage.await.ms=25000

````

**Code pointers:**
- `com.MyridiusUAF.listeners.AiTriageListener`
- `com.MyridiusUAF.utils.reporting.JiraListener`
- `com.MyridiusUAF.base.BaseTest` (screenshot + `@Listeners` wiring)
- `com.MyridiusUAF.utils.reporting.ExtentReportManager` (report logging)

---

## 3) How It Works (Sequence)

1. Test fails (e.g., `TriageFailDemoTest.forceFailure`).
2. **AiTriageListener**:
   - Gathers stack, browser, URL, screenshot path (if any).
   - Asks LLM with a tight triage guide → returns 4 sections.
   - Writes Markdown → `reports/triage/<test>_<ts>.md` and sets `AiTriageMdPath` in context.
   - Logs Markdown to Extent.
   - Counts down `AiTriageLatch`.
3. **JiraListener**:
   - Waits up to `jira.ai.triage.await.ms` for the latch.
   - Attaches Extent HTML, screenshot, and triage `.md` to Jira; posts ADF comment.

---

## 4) What to Show in the Demo

### LLM path
```bash
set OPENAI_API_KEY=YOUR_KEY
set LLM_MODEL=llama-3.1-8b-instant
set OPENAI_ENDPOINT=https://api.groq.com/openai/v1/chat/completions
mvn -q -Dtest=TriageFailDemoTest test
````

**Callouts while it runs**

* Console / Extent show **“AI Triage (LLM)”** with Summary / Root Cause / Evidence / Fix.
* Artifact created at `reports/triage/forceFailure_YYYYMMDD_HHMMSS.md`.
* Jira (`KAN-xx`) contains latest Extent HTML, screenshot, and the triage `.md`; ADF comment summarizes the run.

### Fallback proof (no LLM)

```bash
set OPENAI_API_KEY=
mvn -q -Dtest=TriageFailDemoTest test
```

You’ll see **“AI Triage (Fallback)”** with the same 4 sections and the same artifacts/attachments.

---

## 5) Demo Checklist (What to Point Out)

* **Single source of truth:** the Markdown in `reports/triage/*.md` mirrors Extent and Jira.
* **Observability:** triage appears in Console, Extent, and Jira.
* **Resilience:** no LLM key → Fallback still delivers actionable triage.
* **Actionable format:** 4 sections map to how engineers debug.

---

## 6) Expected Outputs

**Console/Extent:** one block titled **AI Triage (LLM)** or **AI Triage (Fallback)** with the 4 sections.

**Files:**

* `reports/ExecutionReport_<ts>.html`
* `reports/screenshots/<test>_<ts>.png` (on failure)
* `reports/triage/<test>_<ts>.md`

**Jira:**

* Attachments for the above + ADF comment.

---

## 7) Troubleshooting (Fast)

**“No triage markdown found to attach”**

* Ensure `ai.triage.enabled=true`.
* Verify both listeners are active (`@Listeners` on BaseTest or `<listeners>` in suite).
* Confirm the latch keys match on both sides: `AiTriageLatch`, `AiTriageMdPath`.
* Increase `jira.ai.triage.await.ms` (e.g., `45000`) for slow machines.
* Check disk: `reports/triage/<test>_<ts>.md` exists; JiraListener’s disk fallback will pick it up next run.

**Duplicate triage prints**

* We log once to Extent (console print is disabled in `AiTriageListener`).
  If you still see two, make sure there is only **one** listener registration (don’t register in both `@Listeners` and
  XML simultaneously).

**Screenshot missing**

* Confirm `BaseTest.recordExecutionData` saved and set `LastScreenshotPath` in context.

---

## 8) Definition of Done (for Demo)

* Failing test produces triage in **all three places** (Console/Extent, File, Jira).
* Running **with/without** LLM key yields consistent artifacts.
* **No duplicate** triage blocks in logs.

---

## Sample Output Block

### Summary

The test `forceFailure` in `TriageFailDemoTest` is failing due to an intentional failure to demo AI triage. The test
expects `true` but finds `false`.

### Root Cause

Likely a deliberate assertion to showcase triage, or a misconfigured demo assertion causing `assertTrue` to fail.

### Evidence

Stack shows `AssertionError` at `Assert.assertTrue(...)`. Screenshot: `reports/screenshots/forceFailure_<ts>.png`.

### Fix Suggestion

If this is a demo, keep the failure but clarify the message. Otherwise, replace the forced failure with a real assertion
or guard condition and re-run to validate the flow.

```

**Where to put it:**  
Commit this file under your repo, e.g. `docs/runbooks/ai-triage-demo.md` (or `docs/triage/AI-Triage-Quick-Demo-Guide.md`). This keeps all demo/runbook material in one predictable place for engineers and stakeholders.
::contentReference[oaicite:0]{index=0}
```
