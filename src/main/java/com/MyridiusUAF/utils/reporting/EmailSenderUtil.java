package com.MyridiusUAF.utils.reporting;

import com.MyridiusUAF.config.ConfigReader;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Sends the test execution report via SMTP.
 * <p>Recipient addresses are grouped by domain to satisfy Gmail/Workspace relays that
 * restrict multiple destination domains per transaction. If a grouped send fails, the
 * code falls back to per-recipient sends.</p>
 *
 * <p><b>Security:</b> Reads an app/relay password from {@code SMTP_PASSWORD} (env)
 * or a Base64-encoded property via {@link ConfigReader#getDecryptedProperty(String)}.</p>
 */
public final class EmailSenderUtil {

    private static final Logger LOG = LoggerFactory.getLogger(EmailSenderUtil.class);
    private static final String UTF8 = StandardCharsets.UTF_8.name();

    private EmailSenderUtil() { }

    /**
     * Compose and send the execution summary email and attach the Extent report.
     *
     * @param total  total tests
     * @param passed passed tests
     * @param failed failed tests
     */
    public static void sendTestResultEmail(int total, int passed, int failed) {
        // ---- config (no behavioral change) ----------------------------------
        final String host      = ConfigReader.getProperty("smtp.host");
        final String port      = orDefault(ConfigReader.getProperty("smtp.port"), "587");
        final String user      = firstNonBlank(
                ConfigReader.getProperty("smtp.username"),
                ConfigReader.getProperty("smtp.user")
        );
        final String pass      = firstNonBlank(
                System.getenv("SMTP_PASSWORD"),
                ConfigReader.getDecryptedProperty("smtp.password"), // Base64 decode path
                ConfigReader.getProperty("smtp.password"),
                ConfigReader.getProperty("smtp.pass")
        );
        final boolean useAuth  = user != null && !user.isBlank();
        final boolean starttls = Boolean.parseBoolean(orDefault(ConfigReader.getProperty("smtp.starttls"), "true"));
        final String from      = firstNonBlank(ConfigReader.getProperty("email.from"), user); // fallback to username

        // ---- recipients ------------------------------------------------------
        final InternetAddress[] all = parseAddresses(
                ConfigReader.getProperty("email.to"),
                ConfigReader.getProperty("email.cc"),
                ConfigReader.getProperty("email.bcc")
        );
        if (all.length == 0) {
            LOG.warn("No recipients configured; skipping email.");
            return;
        }

        // ---- session (adds sane timeouts, same semantics) --------------------
        final Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", String.valueOf(useAuth));
        props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        props.put("mail.smtp.sendpartial", "true");      // keep partial sends enabled
        props.put("mail.smtp.ssl.trust", host);          // trust given host
        // timeouts (does not change success/failure outcome; avoids indefinite hangs)
        props.put("mail.smtp.connectiontimeout", "20000");
        props.put("mail.smtp.timeout", "20000");
        props.put("mail.smtp.writetimeout", "20000");
        if (from != null && !from.isBlank()) {
            // SMTP envelope sender (what many relays validate)
            props.put("mail.smtp.from", from);
        }

        final Authenticator auth = useAuth ? new Authenticator() {
            @Override protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, pass);
            }
        } : null;

        final Session session = Session.getInstance(props, auth);

        // ---- content ---------------------------------------------------------
        final String subject = String.format("Automation Results — Total:%d  Pass:%d  Fail:%d", total, passed, failed);
        final String body = """
            Hi,

            Please find the attached automation execution report.

            Total: %d
            Passed: %d
            Failed: %d

            Regards,
            UAF
            """.formatted(total, passed, failed);

        final String reportPath = ExtentReportManager.INSTANCE.getReportPath();
        final File reportFile = new File(reportPath);

        // ---- group by destination domain (Gmail/Workspace relay friendly) ----
        final Map<String, List<InternetAddress>> groups = new LinkedHashMap<>();
        for (InternetAddress ia : all) {
            final String addr = ia.getAddress();
            final int at = (addr == null) ? -1 : addr.lastIndexOf('@');
            final String domain = (at > 0) ? addr.substring(at + 1).toLowerCase(Locale.ROOT) : "";
            groups.computeIfAbsent(domain, k -> new ArrayList<>()).add(ia);
        }

        for (var entry : groups.entrySet()) {
            final List<InternetAddress> recipients = entry.getValue();
            try {
                final MimeMessage msg = new MimeMessage(session);
                if (notBlank(from)) msg.setFrom(new InternetAddress(from));
                msg.setRecipients(Message.RecipientType.TO, recipients.toArray(new Address[0]));
                msg.setSubject(subject, UTF8);
                msg.setHeader("X-Auto-Generated", "true");
                msg.setHeader("Auto-Submitted", "auto-generated");
                msg.setSentDate(new Date());

                final MimeMultipart mp = new MimeMultipart();
                final MimeBodyPart text = new MimeBodyPart();
                text.setText(body, UTF8);
                mp.addBodyPart(text);

                if (reportFile.exists()) {
                    final MimeBodyPart att = new MimeBodyPart();
                    att.attachFile(reportFile);
                    att.setFileName(reportFile.getName());
                    mp.addBodyPart(att);
                } else {
                    LOG.warn("Report file not found at {}", reportFile.getAbsolutePath());
                }

                msg.setContent(mp);
                Transport.send(msg);
                LOG.info("Test result email sent to {} recipient(s) in domain {}", recipients.size(), entry.getKey());

            } catch (SendFailedException groupedFailure) {
                LOG.warn("Group send failed for domain {}. Falling back to per-recipient sends.",
                        entry.getKey(), groupedFailure);

                for (InternetAddress ia : recipients) {
                    try {
                        final MimeMessage single = new MimeMessage(session);
                        if (notBlank(from)) single.setFrom(new InternetAddress(from));
                        single.setRecipient(Message.RecipientType.TO, ia);
                        single.setSubject(subject, UTF8);
                        single.setHeader("X-Auto-Generated", "true");
                        single.setHeader("Auto-Submitted", "auto-generated");
                        single.setSentDate(new Date());

                        if (reportFile.exists()) {
                            final MimeBodyPart text = new MimeBodyPart();
                            text.setText(body, UTF8);
                            final MimeBodyPart att = new MimeBodyPart();
                            att.attachFile(reportFile);
                            att.setFileName(reportFile.getName());
                            final MimeMultipart mp = new MimeMultipart();
                            mp.addBodyPart(text);
                            mp.addBodyPart(att);
                            single.setContent(mp);
                        } else {
                            single.setText(body, UTF8);
                        }

                        Transport.send(single);
                        LOG.info("Email sent to {}", ia.toUnicodeString());
                    } catch (Exception e) {
                        LOG.error("Failed to email {}", ia.toUnicodeString(), e);
                    }
                }
            } catch (Exception e) {
                LOG.error("Failed to send email to domain {}", entry.getKey(), e);
            }
        }
    }

    // ----------------- helpers (kept; no deletions) ---------------------------

    private static String orDefault(String v, String d) { return (v == null || v.isBlank()) ? d : v; }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static InternetAddress[] parseAddresses(String... lists) {
        final List<InternetAddress> out = new ArrayList<>();
        if (lists != null) {
            for (String list : lists) {
                if (list == null || list.isBlank()) continue;
                for (String token : list.split("[,;\\s]+")) {
                    final String addr = token.trim();
                    if (addr.isEmpty()) continue;
                    try {
                        out.add(new InternetAddress(addr, true));
                    } catch (AddressException ex) {
                        LOG.warn("Skipping invalid email address: {}", addr);
                    }
                }
            }
        }
        // de-dupe (case-insensitive)
        final Map<String, InternetAddress> map = new LinkedHashMap<>();
        for (var a : out) map.put(a.getAddress().toLowerCase(Locale.ROOT), a);
        return map.values().toArray(new InternetAddress[0]);
    }

    @SuppressWarnings("unused") // intentionally retained per project requirement
    private static String[] splitEmails(String emails) {
        if (emails == null || emails.isBlank()) return new String[0];
        return emails.split("\\s*,\\s*");
    }

    @SuppressWarnings("unused") // intentionally retained per project requirement
    private static void addRecipients(MimeMessage message, Message.RecipientType type, String[] recipients)
            throws MessagingException {
        for (String email : recipients) {
            if (email != null && !email.isBlank()) {
                message.addRecipient(type, new InternetAddress(email.trim()));
            }
        }
    }

    @SuppressWarnings("unused") // intentionally retained per project requirement
    private static String buildHtmlContent(int totalTests, int testsPassed, int testsFailed) {
        final double passPct = totalTests == 0 ? 0 : (testsPassed * 100.0 / totalTests);
        final double failPct = totalTests == 0 ? 0 : (testsFailed * 100.0 / totalTests);
        final String executionDate = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        return """
        <html>
           <body style="font-family: Arial, sans-serif; font-size: 14px; background-color: #f9f9f9;">
              <p>Hi Team,</p>
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
        """.formatted(executionDate, totalTests, testsPassed, passPct, testsFailed, failPct);
    }
}
