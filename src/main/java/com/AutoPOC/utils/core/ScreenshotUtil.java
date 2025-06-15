package com.AutoPOC.utils.core;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Utility class for capturing screenshots during test execution.
 * <p>
 * Saves screenshots under the reports/screenshots/ directory with timestamped filenames.
 */
public class ScreenshotUtil {

    private static final Logger logger = LoggerFactory.getLogger(ScreenshotUtil.class);
    private static final String SCREENSHOT_FOLDER = "reports/screenshots/";

    /**
     * Captures a screenshot of the current WebDriver page and saves it as a PNG file.
     *
     * @param driver         WebDriver instance
     * @param screenshotName Logical name for the screenshot file (will have timestamp appended)
     * @return Relative path to the saved screenshot for embedding in reports; null if failure
     */
    public static String saveScreenshotAsPNG(WebDriver driver, String screenshotName) {
        try {
            if (driver == null) {
                logger.error("WebDriver instance is null. Cannot capture screenshot.");
                return null;
            }

            File srcFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);

            ensureScreenshotFolderExists();

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = screenshotName + "_" + timestamp + ".png";
            String fullPath = SCREENSHOT_FOLDER + fileName;

            FileUtils.copyFile(srcFile, new File(fullPath));

            logger.info("Screenshot saved: {}", fullPath);
            return "screenshots/" + fileName; // Relative path for embedding in HTML report

        } catch (IOException e) {
            logger.error("Failed to save screenshot.", e);
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error while capturing screenshot.", e);
            return null;
        }
    }

    /**
     * Ensures that the screenshot output directory exists.
     * Creates the directory if it does not already exist.
     */
    private static void ensureScreenshotFolderExists() {
        File folder = new File(SCREENSHOT_FOLDER);
        if (!folder.exists() && folder.mkdirs()) {
            logger.info("Screenshot directory created: {}", SCREENSHOT_FOLDER);
        }
    }
}
