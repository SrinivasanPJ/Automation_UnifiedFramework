package com.MyridiusUAF.SystemTest;

import com.MyridiusUAF.utils.annotations.Jira;
import com.MyridiusUAF.base.BaseTest;
import com.MyridiusUAF.utils.annotations.TestType;
import org.testng.ITestContext;
import org.testng.annotations.Test;

/**
 * SystemTest: Validates login functionality using credentials from Excel test data.
 * <p>
 * - Maps test case to Jira via @Jira("KAN-3")
 * - Reads input from Synthetic Data sheet (InputID: Ip1, TestID: 1)
 * - Fails test if login is unsuccessful
 */
// System test
@TestType(TestType.Kind.SYSTEM)
public class LoginTest extends BaseTest {

    /**
     * Logs in with user credentials.
     * Input is read from Excel (TestID: "1", InputID: "Ip1").
     * Test result is tracked in reporting and Excel.
     *
     * @param context TestNG test context
     */
    @Jira("KAN-3")
    @Test(description = "Login to application with User credentials", priority = 1)
    public void loginUser(ITestContext context) {
        initializeTestContext("1", "Ip1", context);
        performLogin("1");
    }
}
