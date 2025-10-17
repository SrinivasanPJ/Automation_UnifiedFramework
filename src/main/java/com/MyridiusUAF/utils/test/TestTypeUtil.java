package com.MyridiusUAF.utils.test;

import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.annotations.TestType;
import org.testng.ITestResult;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Classifies tests as SYSTEM/E2E based on (in order):
 * <ol>
 *   <li>@TestType on method or class</li>
 *   <li>TestNG groups (configurable names)</li>
 *   <li>Config-driven package prefixes (CSV)</li>
 *   <li>Legacy hardcoded package prefixes</li>
 * </ol>
 * Provides stack-based helpers for callers that aren't holding an {@link ITestResult}.
 */
public final class TestTypeUtil {

    // Legacy defaults (kept for back-compat)
    private static final String LEGACY_SYSTEM_PFX = "com.MyridiusUAF.SystemTest.";
    private static final String LEGACY_E2E_PFX = "com.MyridiusUAF.EndToEnd.";

    private TestTypeUtil() { /* no instances */ }

    // --------- Public API used by listeners ---------

    public static boolean isSystem(ITestResult result) {
        return classify(result) == Kind.SYSTEM;
    }

    public static boolean isE2E(ITestResult result) {
        return classify(result) == Kind.E2E;
    }

    /**
     * Legacy helper: by class name.
     */
    public static boolean isSystemTestClass(String className) {
        if (className == null) return false;
        if (startsWithAny(packageNameOf(className), getCsv("testtype.system.packages", LEGACY_SYSTEM_PFX))) return true;
        return className.startsWith(LEGACY_SYSTEM_PFX);
    }

    /**
     * Stack-based detection for callers without an ITestResult (system).
     */
    public static boolean isFromSystemTestPackage() {
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            String cn = e.getClassName();
            if (cn != null && (cn.startsWith(LEGACY_SYSTEM_PFX)
                    || startsWithAny(packageNameOf(cn), getCsv("testtype.system.packages", LEGACY_SYSTEM_PFX)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stack-based detection for callers without an ITestResult (e2e).
     */
    public static boolean isFromE2ETestPackage() {
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            String cn = e.getClassName();
            if (cn != null && (cn.startsWith(LEGACY_E2E_PFX)
                    || startsWithAny(packageNameOf(cn), getCsv("testtype.e2e.packages", LEGACY_E2E_PFX)))) {
                return true;
            }
        }
        return false;
    }

    // --------- Core classification logic ---------

    private static Kind classify(ITestResult result) {
        if (result == null || result.getTestClass() == null) return Kind.UNKNOWN;

        Class<?> cls = result.getTestClass().getRealClass();
        Method m = result.getMethod() != null
                ? result.getMethod().getConstructorOrMethod().getMethod()
                : null;

        // 1) Annotation (method > class)
        TestType annM = (m != null) ? m.getAnnotation(TestType.class) : null;
        if (annM != null) return toKind(annM.value());
        TestType annC = cls.getAnnotation(TestType.class);
        if (annC != null) return toKind(annC.value());

        // 2) TestNG groups
        String sysGroup = propOr("testtype.group.system", "system");
        String e2eGroup = propOr("testtype.group.e2e", "e2e");
        for (String g : result.getMethod().getGroups()) {
            if (equalsIgnoreCase(g, e2eGroup)) return Kind.E2E;
            if (equalsIgnoreCase(g, sysGroup)) return Kind.SYSTEM;
        }

        // 3) Config-driven package prefixes (CSV)
        String pkg = safePackageName(cls);
        if (startsWithAny(pkg, getCsv("testtype.e2e.packages", LEGACY_E2E_PFX))) return Kind.E2E;
        if (startsWithAny(pkg, getCsv("testtype.system.packages", LEGACY_SYSTEM_PFX))) return Kind.SYSTEM;

        // 4) Legacy hardcoded fallback
        if (pkg.startsWith(LEGACY_E2E_PFX)) return Kind.E2E;
        if (pkg.startsWith(LEGACY_SYSTEM_PFX)) return Kind.SYSTEM;

        return Kind.UNKNOWN;
    }

    private static Kind toKind(TestType.Kind k) {
        return (k == TestType.Kind.E2E) ? Kind.E2E : Kind.SYSTEM;
    }

    private static String safePackageName(Class<?> c) {
        try {
            return c.getPackageName() + ".";
        } catch (Throwable t) {
            Package p = c.getPackage();
            return (p != null ? p.getName() : "") + ".";
        }
    }

    // --------- Small helpers ---------

    private static String packageNameOf(String className) {
        int i = className.lastIndexOf('.');
        return (i > 0 ? className.substring(0, i + 1) : "");
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private static boolean startsWithAny(String value, List<String> prefixes) {
        if (value == null || prefixes == null) return false;
        for (String p : prefixes) {
            if (p == null || p.isBlank()) continue;
            String norm = p.endsWith(".") ? p : (p + ".");
            if (value.startsWith(norm)) return true;
        }
        return false;
    }

    private static List<String> getCsv(String key, String... defaults) {
        String raw = safeProp(key);
        if (raw == null || raw.isBlank()) return Arrays.asList(defaults);
        String[] parts = raw.split(",");
        List<String> out = new ArrayList<>(parts.length);
        for (String s : parts) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out.isEmpty() ? Arrays.asList(defaults) : out;
    }

    private static String propOr(String key, String def) {
        String v = safeProp(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    private static String safeProp(String key) {
        try {
            return ConfigReader.getProperty(key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // Extra helper for callers that only have class name
    public static boolean isE2ETestClass(String className) {
        return className != null &&
                (className.startsWith(LEGACY_E2E_PFX)
                        || startsWithAny(packageNameOf(className), getCsv("testtype.e2e.packages", LEGACY_E2E_PFX)));
    }

    public enum Kind {SYSTEM, E2E, UNKNOWN}
}
