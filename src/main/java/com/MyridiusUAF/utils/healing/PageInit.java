package com.MyridiusUAF.utils.healing;

import com.MyridiusUAF.ai.AiSwitches;
import com.MyridiusUAF.ai.LlmClient;
import com.MyridiusUAF.ai.LlmClientFactory;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.PageFactory;

public final class PageInit {
    private PageInit() {
    }

    public static void init(WebDriver driver, Object page) {
        if (!AiSwitches.selfHealingEnabled()) {
            PageFactory.initElements(driver, page);
            return;
        }
        // Heuristics always; LLM only if both switches allow and key present
        LlmClient llm = AiSwitches.healingUseLlm() ? LlmClientFactory.maybeCreate() : null;
        PageFactory.initElements(new HealingFieldDecorator(driver, llm), page);
    }
}
