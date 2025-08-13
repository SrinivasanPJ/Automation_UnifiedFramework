package com.MyridiusUAF.SystemTest;

import com.MyridiusUAF.base.BaseTest;
import com.MyridiusUAF.utils.annotations.TestType;
import com.MyridiusUAF.utils.data.RandomDataGenerator;
import com.MyridiusUAF.utils.data.TestDataUpdater;
import org.testng.ITestContext;
import org.testng.annotations.Test;

/**
 * SystemTest: Registers a new user with random credentials and validates registration.
 * <p>
 * Steps:
 * <ul>
 *   <li>Initializes synthetic test context</li>
 *   <li>Navigates to registration, enters random details</li>
 *   <li>Persists the new credentials to Excel for traceability</li>
 *   <li>Validates registration success and logs out</li>
 * </ul>
 */
// End to End test
@TestType(TestType.Kind.SYSTEM)
public class RegisterTest extends BaseTest {

    /**
     * Registers a user with generated data, persists the credentials,
     * and verifies registration success.
     *
     * @param context TestNG context for test execution
     */
    @Test(description = "Registers a new user with randomly generated valid data", priority = 1)
    public void registerUser(ITestContext context) {
        // 1. Prepare test context and input data
        initializeTestContext("1", "Ip1", context);

        // 2. Open registration page
        registerPage.clickRegisterLink();
        registerPage.waitForRegisterPage();
        registerPage.selectGender("Male89");

        // 3. Generate and enter user data
        String firstName = RandomDataGenerator.getRandomFirstName();
        String lastName = RandomDataGenerator.getRandomLastName();
        String randomEmail = RandomDataGenerator.getRandomEmail();
        String randomPassword = RandomDataGenerator.getRandomPassword(10);

        registerPage.enterFirstName(firstName);
        registerPage.enterLastName(lastName);

        // 4. Update Excel sheet for future test reference (traceability)
        TestDataUpdater.updateUsernameAndPassword("1", randomEmail, randomPassword);

        // 5. Complete registration
        registerPage.enterEmail(randomEmail);
        registerPage.enterPassword(randomPassword);
        registerPage.enterConfirmPassword(randomPassword);
        registerPage.clickRegisterButton();
        registerPage.verifyRegistrationSuccess(15);

        // 6. Log out to complete user lifecycle
        loginPage.clickLogoutLink();
    }
}
