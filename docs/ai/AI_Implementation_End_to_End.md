# AI Implementation End-to-End (MyridiusUAF)

> This document describes the AI implementation in this repository as it exists today. It covers architecture, configuration, tools/libraries, agents, pages/tests, and run flows.

## 1) Goals and Scope

The AI layer supports:
- **Test authoring**: generate TestNG test classes from user stories.
- **Page authoring**: generate Page Object classes from descriptions.
- **Synthetic data generation**: append AI-generated rows to Excel test data (with deterministic fallback).
- **Self-healing (optional)**: AI-assisted locator healing (fallback to heuristics when AI is off).

## 2) Key Packages and Files

### Core AI Modules
- `src/main/java/com/MyridiusUAF/ai/`
  - `LlmClient.java`: LLM abstraction interface.
  - `LlmClientFactory.java`: central factory; returns OpenAI client when enabled.
  - `AiSwitches.java`: feature flags (self-healing, authoring, triage, data-gen, openai enabled).

### OpenAI Integration
- `src/main/java/com/MyridiusUAF/ai/openai/`
  - `OpenAiConfig.java`: reads OpenAI settings from `ConfigReader`.
  - `OpenAiLlmClient.java`: HTTP client for Chat Completions, token logging, fallback model handling.
  - `OpenAiLlmClientFactory.java`: builds `OpenAiLlmClient` from config.

### AI Agents
- `src/main/java/com/MyridiusUAF/ai/agents/`
  - `TestAuthorAgent.java`: generates TestNG tests from user stories.
  - `PageAuthorAgent.java`: generates Page Objects from descriptions.
  - `SyntheticDataAgent.java`: generates rows for `Synthetic_Data` sheet, with fallback on errors.

### CLI Tools
- `src/main/java/com/MyridiusUAF/cli/`
  - `AiGenerateTest.java`: generates tests from user stories.
  - `AiGeneratePage.java`: generates Page Objects.
  - `AiGenerateSyntheticData.java`: appends synthetic rows to Excel.

### Config and Data
- `src/main/java/com/MyridiusUAF/config/ConfigReader.java`: unified config reader.
- `src/test/resources/config.properties`: main configuration file.
- `src/test/resources/POC_data_sheet_v1.1.xlsx`: Excel data used by AI data generation.
- `src/main/resources/prompts/`: optional prompt resources (authoring/triage).

### Pages and Tests
- `src/main/java/com/MyridiusUAF/pages/`: Page Objects.
- `src/test/java/com/MyridiusUAF/tests/`: Tests (AI-generated and manual).

## 3) Tools and Libraries Used

### Runtime / Build
- Java (JDK 23 used in your run logs)
- Maven (build + exec plugin for CLI tools)

### AI / HTTP / JSON
- OpenAI Chat Completions API via `java.net.http.HttpClient`
- Jackson (`com.fasterxml.jackson`) for JSON parsing

### Selenium / Testing
- Selenium WebDriver
- TestNG
- Logback / SLF4J

## 4) Configuration (Single Source of Truth)

All AI config is resolved through `ConfigReader` using `config.properties` and `${ENV_VAR}` expansion.

### Required AI settings (in `config.properties`)

```ini
# Enable AI
ai.openai.enabled=true
ai.authoring.enabled=true
ai.datagen.enabled=true

# OpenAI (use env for secrets)
openai.apiKey=${OPENAI_API_KEY}
openai.baseUrl=https://api.openai.com
openai.model=gpt-4.1
openai.fallbackModel=gpt-4o-mini
openai.timeoutSeconds=20
```

### Required Environment Variables

```powershell
$env:OPENAI_API_KEY="sk-proj-..."
```

## 5) AI Client Behavior

### Model Selection & Fallback
- `OpenAiLlmClient` uses `openai.model` by default.
- If the model is not accessible (403/404/400 model errors), it retries once with `openai.fallbackModel`.

### Token Usage Logging
Each LLM call logs:
- Model used
- Base URL
- Prompt, completion, total tokens

End of execution logs:
- Session summary totals (`logSessionUsage()`)

## 6) Self-Healing Integration

- `BasePage` and `PageInit` use `AiSwitches` to decide LLM usage.
- If AI is off or no key is present, the framework falls back to heuristic healing.

## 7) AI Page Authoring Flow

### Command (IntelliJ Run Configuration)
Use `AiGeneratePage` with a description, class name, and package.

Example:

```text
Demo Web Shop product listing page with category filter, sort dropdown and add-to-cart buttons ProductListPage com.MyridiusUAF.pages
```

This generates:
- `src/main/java/com/MyridiusUAF/pages/ProductListPage.java`

### Important Constraints enforced by `PageAuthorAgent`
- Pages extend `BasePage`
- Uses only BasePage methods (click, sendKeys, selectByVisibleText, etc.)
- No direct WebDriver instantiation

## 8) AI Test Authoring Flow

### Recommended Prompt (uses ProductListPage)

```text
End-to-end product listing flow: login as existing user, navigate category "Computers", sort by "Price: Low to High", add the first product to cart, verify user is logged in ProductListFlowAiTest com.MyridiusUAF.tests.ai
```

This generates:
- `src/test/java/com/MyridiusUAF/tests/ai/ProductListFlowAiTest.java`

### Constraints enforced by `TestAuthorAgent`
- Extends `BaseTest`
- Uses only these imports:
  - `org.testng.annotations.Test`
  - `org.testng.ITestContext`
  - `org.testng.Assert`
  - `com.MyridiusUAF.base.BaseTest`
- Must use available Page Objects (including `ProductListPage`)
- Uses BaseTest helpers for data and login

## 9) AI Synthetic Data Generation

### Command

```text
AiGenerateSyntheticData 5 Demo Web Shop login/logout flows for existing users
```

Behavior:
- Reads `Synthetic_Data` sheet from Excel
- Appends 5 rows after the last `IpX`
- Uses LLM if available, otherwise deterministic fallback rows

## 10) Page Objects Available to Tests

Available protected fields on `BaseTest` include:
- `LoginPage loginPage`
- `RegisterPage registerPage`
- `ProductListPage productListPage`
- `AddProductsToCartAndPlaceOrderPage addProductsToCartAndPlaceOrderPage`
- `OrderInformationPage orderInformationPage`

## 11) LoginPage Methods (Supported by AI Tests)

The following methods exist on `LoginPage` and are safe for AI-generated tests:
- `login(String user, String pass)`
- `enterEmail(String user)`
- `enterPassword(String pass)`
- `clickLoginButton()`
- `clickLogoutLink()`
- `isLoggedIn()`
- `isLoggedOut()`

## 12) Common Error Causes and Fixes

### 401 Invalid API Key
- Ensure `OPENAI_API_KEY` is set in environment.
- Ensure `openai.apiKey=${OPENAI_API_KEY}` in config.

### 403/404 Model Not Found
- Change `openai.model` to a model your key has access to.
- Keep `openai.fallbackModel` as a safe option.

### 429 Insufficient Quota
- Add billing or use a key with available quota.

### IDE Red Errors for Methods
- Rebuild project or invalidate IntelliJ caches.
- Ensure page objects are initialized in `BaseTest`.

## 13) Quick Run Examples

### Generate Page
```text
AiGeneratePage
Demo Web Shop product listing page with category filter, sort dropdown and add-to-cart buttons ProductListPage com.MyridiusUAF.pages
```

### Generate Test
```text
AiGenerateTest
End-to-end product listing flow: login as existing user, navigate category "Computers", sort by "Price: Low to High", add the first product to cart, verify user is logged in ProductListFlowAiTest com.MyridiusUAF.tests.ai
```

### Generate Synthetic Data
```text
AiGenerateSyntheticData
5 Demo Web Shop login/logout flows for existing users
```

## 14) What’s Already Fixed in This Repo

- `ConfigReader` expands `${ENV_VAR}` for all properties
- OpenAI model fallback on access errors
- Token logging per LLM call and session summary
- `ProductListPage` integrated into `BaseTest`
- Test authoring prompt enforces `ProductListPage` use
- Page authoring prompt uses valid `BasePage` methods

## 15) Next Maintenance Checklist

- Keep AI prompts aligned with actual page object methods
- Add new page objects to `BaseTest` if tests must use them
- Use `openai.model` + `openai.fallbackModel` to avoid model access failures
- Rebuild IntelliJ after generating new pages/tests

