package com.MyridiusUAF.tests.ai;

import com.MyridiusUAF.ai.LlmClient;
import com.MyridiusUAF.ai.agents.DataGenAgent;
import com.MyridiusUAF.ai.clients.OpenAiClient;
import com.MyridiusUAF.base.BaseTest;
import com.MyridiusUAF.utils.annotations.TestType;
import com.MyridiusUAF.utils.data.TestDataUpdater;
import com.MyridiusUAF.utils.reporting.ExtentReportManager;
import org.testng.ITestContext;
import org.testng.annotations.Test;

import java.util.Map;

@TestType(TestType.Kind.SYSTEM)
public class RegisterAiDataDemoTest extends BaseTest {

    private static String unq(String v) {
        return v == null ? "" : v.replaceAll("^\"|\"$", "");
    }

    private static String mask(Map<String, String> m) {
        String email = maskEmail(unq(m.getOrDefault("email", "-")));
        String phone = unq(m.getOrDefault("phone", "-")).replaceAll(".(?=.{2})", "*");
        return "{firstName=" + unq(m.get("firstName")) +
                ", lastName=" + unq(m.get("lastName")) +
                ", email=" + email +
                ", phone=" + phone +
                ", address=" + unq(m.get("address")) + "}";
    }

    private static String maskEmail(String email) {
        return email == null ? "-" : email.replaceAll("(^.).*(@.*$)", "$1***$2");
    }

    @Test(description = "Registers a new user using AI-generated profile; falls back when LLM unavailable")
    public void registerWithSmartData(final ITestContext context) {
        initializeTestContext("1", "Ip1", context);

        LlmClient llm = null;
        try {
            llm = new OpenAiClient();
        } catch (Exception ignored) {
        }
        DataGenAgent agent = new DataGenAgent(llm);

        Map<String, String> p = agent.userProfile();
        String firstName = unq(p.get("firstName"));
        String lastName = unq(p.get("lastName"));
        String email = unq(p.get("email"));
        String password = "P@ssw0rd123";

        boolean looksFallback = "Alex".equals(firstName) && email.endsWith("@example.com");
        boolean incomplete = firstName.isBlank() || lastName.isBlank() || email.isBlank();

        if (incomplete) {
            ExtentReportManager.INSTANCE.logInfo("LLM profile incomplete → switching to fallback", getClass());
            p = new DataGenAgent(null).userProfile();
            firstName = unq(p.get("firstName"));
            lastName = unq(p.get("lastName"));
            email = unq(p.get("email"));
            looksFallback = true;
        }

        ExtentReportManager.INSTANCE.logInfo("AI Data Source: " + (looksFallback ? "Fallback (no/invalid model)" : "LLM"), getClass());
        ExtentReportManager.INSTANCE.logInfo("AI Profile (masked): " + mask(p), getClass());

        // same flow as RegisterTest
        registerPage.clickRegisterLink();
        registerPage.waitForRegisterPage();
        registerPage.selectGender("Male");

        registerPage.enterFirstName(firstName);
        registerPage.enterLastName(lastName);

        TestDataUpdater.updateUsernameAndPassword("1", email, password);

        registerPage.enterEmail(email);
        registerPage.enterPassword(password);
        registerPage.enterConfirmPassword(password);
        registerPage.clickRegisterButton();
        registerPage.verifyRegistrationSuccess(15);

        loginPage.clickLogoutLink();
        ExtentReportManager.INSTANCE.logInfo("Registration completed for: " + maskEmail(email), getClass());
    }
}