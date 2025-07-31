package com.MyridiusUAF.utils.reporting;

import com.MyridiusUAF.config.ConfigReader;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Utility class for sending test execution summary emails with HTML content,
 * inline logo, and attached report, using enterprise-grade security.
 */
public final class EmailSenderUtil {

    private static final Logger logger = LoggerFactory.getLogger(EmailSenderUtil.class);

    // Configuration values
    private static final String SMTP_HOST = ConfigReader.getProperty("smtp.host");
    private static final String SMTP_PORT = ConfigReader.getProperty("smtp.port");
    private static final String DISPLAY_NAME = ConfigReader.getProperty("email.display.name");
    private static final String USERNAME = ConfigReader.getProperty("smtp.username");
    private static final String PASSWORD = System.getenv("SMTP_PASSWORD") != null
            ? System.getenv("SMTP_PASSWORD")
            : ConfigReader.getDecryptedProperty("smtp.password");

    private static final String FROM = ConfigReader.getProperty("email.from");
    private static final String[] TO_RECIPIENTS = splitEmails(ConfigReader.getProperty("email.to"));
    private static final String[] CC_RECIPIENTS = splitEmails(ConfigReader.getProperty("email.cc"));
    private static final String[] BCC_RECIPIENTS = splitEmails(ConfigReader.getProperty("email.bcc"));

    private EmailSenderUtil() {}

    /**
     * Sends an automated test execution summary email with summary, HTML report, and inline logo.
     * Recipients and SMTP config are sourced from application config.
     *
     * @param totalTests  total executed
     * @param testsPassed number passed
     * @param testsFailed number failed
     */
    public static void sendTestResultEmail(int totalTests, int testsPassed, int testsFailed) {
        String reportPath = ExtentReportManager.INSTANCE.getReportPath();
        logger.info("Attaching report from: {}", reportPath);

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(USERNAME, PASSWORD);
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);

            // Set from with fallback if encoding fails
            try {
                message.setFrom(new InternetAddress(FROM, DISPLAY_NAME));
            } catch (UnsupportedEncodingException e) {
                logger.warn("Failed to apply display name. Using plain email.");
                message.setFrom(new InternetAddress(FROM));
            }

            addRecipients(message, Message.RecipientType.TO, TO_RECIPIENTS);
            addRecipients(message, Message.RecipientType.CC, CC_RECIPIENTS);
            addRecipients(message, Message.RecipientType.BCC, BCC_RECIPIENTS);

            String subjectTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            message.setSubject("Automation POC - Test Execution Report - " + subjectTime);

            Multipart multipart = new MimeMultipart("related");

            // 1. HTML summary
            MimeBodyPart htmlBody = new MimeBodyPart();
            String htmlContent = buildHtmlContent(totalTests, testsPassed, testsFailed);
            htmlBody.setContent(htmlContent, "text/html; charset=utf-8");
            multipart.addBodyPart(htmlBody);

            // 2. Attach HTML report
            if (reportPath != null && !reportPath.isBlank() && new File(reportPath).exists()) {
                MimeBodyPart attachmentPart = new MimeBodyPart();
                attachmentPart.attachFile(new File(reportPath));
                multipart.addBodyPart(attachmentPart);
            } else {
                logger.warn("HTML report file not found for attachment: {}", reportPath);
            }

            message.setContent(multipart);

            Transport.send(message);
            logger.info("Test execution report email sent successfully!");

        } catch (Exception e) {
            logger.error("Failed to send test result email.", e);
        }
    }

    /**
     * Utility: splits a comma-separated email string into an array, handles null/empty.
     */
    private static String[] splitEmails(String emails) {
        if (emails == null || emails.isBlank()) return new String[0];
        return emails.split("\\s*,\\s*");
    }

    /**
     * Adds multiple recipients to the MimeMessage.
     */
    private static void addRecipients(MimeMessage message, Message.RecipientType type, String[] recipients)
            throws MessagingException {
        for (String email : recipients) {
            if (!email.isBlank()) {
                message.addRecipient(type, new InternetAddress(email.trim()));
            }
        }
    }

    /**
     * Builds HTML body summarizing the test execution, including logo.
     */
    private static String buildHtmlContent(int totalTests, int testsPassed, int testsFailed) {
        double passPct = totalTests == 0 ? 0 : (testsPassed * 100.0 / totalTests);
        double failPct = totalTests == 0 ? 0 : (testsFailed * 100.0 / totalTests);
        String executionDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        return """
        <html>
           <body style="font-family: Arial, sans-serif; font-size: 14px; background-color: #f9f9f9;">
              <table width="100%%" style="border: none; margin: 50; padding: 0;">
                 <tr style="vertical-align: middle;">
                    <td align="left">
                       <h2 style="color: #007B04; margin: -30px 0 0 0;">Project: <span style="font-weight: bold;">Automation POC</span></h2>
                    </td>
                    <td align="right">
                       <img src="https://www.bizztracker.com/wp-content/uploads/2019/11/shutterstock_1257993892-1350x600-1.jpg"
                          alt="POC Logo" width="250" height="100" style="margin: 0;" />
                    </td>
                 </tr>
              </table>
              <p style="margin-top: -25px;">Hi Team,</p>
              <p>The automation execution has completed. Please find the summary below:</p>
              <table border="1" cellpadding="10" cellspacing="0" style="border-collapse: collapse; width: 60%%;">
                 <thead style="background-color: #e6f7ff;">
                    <tr style="text-align: center;">
                       <th>Execution Date</th>
                       <th>Total Tests</th>
                       <th style="color: green;">Passed</th>
                       <th style="color: red;">Failed</th>
                    </tr>
                 </thead>
                 <tbody>
                    <tr style="text-align: center;">
                       <td>%s</td>
                       <td>%d</td>
                       <td style="color: green;">%d (%.2f%%)</td>
                       <td style="color: red;">%d (%.2f%%)</td>
                    </tr>
                 </tbody>
              </table>
              <p>Please check the attached report for detailed logs and screenshots.</p>
              <p style="color: #999;">This is an automated email from the Automation Framework.</p>
           </body>
        </html>
        """.formatted(
                executionDate, totalTests, testsPassed, passPct, testsFailed, failPct
        );
    }
}
