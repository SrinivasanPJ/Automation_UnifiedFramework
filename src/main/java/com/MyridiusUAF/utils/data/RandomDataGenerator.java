package com.MyridiusUAF.utils.data;

import java.util.Random;

/**
 * Utility class for generating random test data such as names, usernames, emails, and passwords.
 * <p>
 * Used in registration, account creation, and negative test scenarios.
 * <ul>
 *   <li>Non-instantiable static utility class</li>
 *   <li>Configurable sample data for Indian names and common email domains</li>
 *   <li>Strong random password generation</li>
 * </ul>
 */
public final class RandomDataGenerator {

    private static final String[] FIRST_NAMES = {
            "Priya", "Amit", "Sneha", "Rahul", "Kiran", "Riya", "Arjun", "Divya", "Vikram", "Neha",
            "Suman", "Vikas", "Meera", "Ajay", "Sunita", "Tarun", "Sonal", "Kabir", "Ankita", "Rohan"
    };

    private static final String[] LAST_NAMES = {
            "Sharma", "Rao", "Mehra", "Patel", "Kumar", "Desai", "Kapoor", "Reddy", "Singh", "Iyer",
            "Joshi", "Pillai", "Gupta", "Verma", "Nair", "Das", "Yadav", "Chopra", "Agarwal", "Kulkarni"
    };

    private static final String[] EMAIL_DOMAINS = {
            "@gmail.com", "@yahoo.com", "@outlook.com", "@hotmail.com", "@example.com"
    };

    private static final Random RANDOM = new Random();

    // Prevent instantiation
    private RandomDataGenerator() {}

    /**
     * Returns a random Indian first name.
     */
    public static String getRandomFirstName() {
        return FIRST_NAMES[RANDOM.nextInt(FIRST_NAMES.length)];
    }

    /**
     * Returns a random Indian last name.
     */
    public static String getRandomLastName() {
        return LAST_NAMES[RANDOM.nextInt(LAST_NAMES.length)];
    }

    /**
     * Returns a random username in the format first.last### (lowercase).
     */
    public static String getRandomUsername() {
        String firstName = getRandomFirstName();
        String lastName = getRandomLastName();
        int number = 100 + RANDOM.nextInt(900); // 3-digit number
        return (firstName + "." + lastName + number).toLowerCase();
    }

    /**
     * Returns a random email address using the username and a random domain.
     */
    public static String getRandomEmail() {
        String username = getRandomUsername();
        String domain = EMAIL_DOMAINS[RANDOM.nextInt(EMAIL_DOMAINS.length)];
        return username + domain;
    }

    /**
     * Generates a random strong password containing uppercase, lowercase, digits, and symbols.
     * @param length desired password length
     * @return generated password string
     */
    public static String getRandomPassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
