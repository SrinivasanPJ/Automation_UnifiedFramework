package com.MyridiusUAF.tests.ai;

import com.MyridiusUAF.base.BaseTest;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.annotations.Test;

public class TriageFailDemoTest extends BaseTest {
    @Test
    public void forceFailure(final ITestContext ctx) {
        // Minimal boot to populate context (Browser, ApplicationUrl, etc.)
        executeTestForTestID("1", ctx); // navigates to URL and wires pages
        // Force an assertion failure to trigger the listener
        Assert.assertTrue(false, "Intentional failure to demo AI triage");
    }
}
