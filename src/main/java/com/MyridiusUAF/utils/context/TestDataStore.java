package com.MyridiusUAF.utils.context;

/**
 * Constant
 * Ensures
 */

public class TestDataStore
{
    private static String startAppFullName;
    public static void setStartFullName(String name) {
        startAppFullName = name;
    }
    public static String getStartAppFullName() {
        return startAppFullName;
    }

}

