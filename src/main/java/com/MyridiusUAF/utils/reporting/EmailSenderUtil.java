package com.MyridiusUAF.utils.reporting;

import com.MyridiusUAF.config.ConfigReader;
import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.*;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Sends the test execution report via SMTP.
 *
 * - Preserves TO/CC/BCC roles and groups by destination domain (relay-friendly).
 * - Falls back to per-recipient sends on grouped failure.
 * - HTML template with header, metrics table, inline logo (CID), and CTA button.
 * - Button:
 *      * if email.report.url is set -> opens in new tab
 *      * if empty -> links to attached report via cid:report (download attr)
 *        (Gmail may block button-download; the normal attachment remains visible)
 * - Display name (email.display.name)
 * - SMTP password from env SMTP_PASSWORD or Base64-decoded property
 * - Robust timeouts; de-dup recipients; strict address validation
 */
public final class EmailSenderUtil {

    private static final Logger LOG = LoggerFactory.getLogger(EmailSenderUtil.class);
    private static final String UTF8 = StandardCharsets.UTF_8.name();

    private static final String PROJECT_NAME =
            Optional.ofNullable(ConfigReader.getProperty("email.body.projectName"))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .orElse("Myridius Unified Framework");

    private EmailSenderUtil() {}

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------
    public static void sendTestResultEmail(int totalTests, int testsPassed, int testsFailed) {
        // ---- configuration ----
        final String host       = ConfigReader.getProperty("smtp.host");
        final String port       = orDefault(ConfigReader.getProperty("smtp.port"), "587");
        final boolean starttls  = Boolean.parseBoolean(orDefault(ConfigReader.getProperty("smtp.starttls"), "true"));
        final String username   = firstNonBlank(ConfigReader.getProperty("smtp.username"),
                ConfigReader.getProperty("smtp.user"));
        final String password   = firstNonBlank(System.getenv("SMTP_PASSWORD"),
                ConfigReader.getDecryptedProperty("smtp.password"),
                ConfigReader.getProperty("smtp.password"),
                ConfigReader.getProperty("smtp.pass"));

        final String fromEmail  = ConfigReader.getProperty("email.from");
        final String fromName   = ConfigReader.getProperty("email.display.name");

        // recipients (CSV)
        final InternetAddress[] to  = parseAddresses(ConfigReader.getProperty("email.to"));
        final InternetAddress[] cc  = parseAddresses(ConfigReader.getProperty("email.cc"));
        final InternetAddress[] bcc = parseAddresses(ConfigReader.getProperty("email.bcc"));

        if (to.length + cc.length + bcc.length == 0) {
            LOG.warn("Email not sent: no recipients configured.");
            return;
        }

        // ---- session ----
        final Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", !isBlank(username) ? "true" : "false");
        props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        props.put("mail.smtp.sendpartial", "true");
        props.put("mail.smtp.ssl.trust", host);
        props.put("mail.smtp.connectiontimeout", "20000");
        props.put("mail.smtp.timeout", "20000");
        props.put("mail.smtp.writetimeout", "20000");
        if (!isBlank(fromEmail)) props.put("mail.smtp.from", fromEmail); // envelope sender

        final Session session = Session.getInstance(props,
                isBlank(username) ? null : new Authenticator() {
                    @Override protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });

        // ---- content ----
        final String subjectTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        final String subject = orDefault(ConfigReader.getProperty("email.subject"),
                "Automation Test Execution Report") + " - " + subjectTime;

        final String reportPath = ExtentReportManager.INSTANCE.getReportPath();
        final File   reportFile = isBlank(reportPath) ? null : new File(reportPath);

        // Button link (hosted or cid:report)
        final String rawReportUrl = ConfigReader.getProperty("email.report.url");
        final String reportUrl = buildReportUrl(rawReportUrl, reportFile);
        final boolean hasHostedUrl = !isBlank(reportUrl) &&
                (reportUrl.startsWith("http://") || reportUrl.startsWith("https://"));
        final String buttonHref = hasHostedUrl ? reportUrl : "cid:report";

        // Inline logo (optional)
        final File logoFile = resolveLogoFile(ConfigReader.getProperty("email.logo.path"));

        // Layout knobs you can change in config
        final String alignProp   = orDefault(ConfigReader.getProperty("email.layout.align"), "center");
        final String containerMargin = "left".equalsIgnoreCase(alignProp) ? "0" : "0 auto";
        final String bodyPadding = orDefault(ConfigReader.getProperty("email.layout.bodyPadding"), "20px");

        final String htmlBody = buildHtmlContent(
                PROJECT_NAME, totalTests, testsPassed, testsFailed,
                hasHostedUrl, buttonHref, containerMargin, bodyPadding
        );

        // ---- group by domain (role-aware) ----
        Map<String, RoleBuckets> groups = bucketByDomain(to, cc, bcc);

        for (Map.Entry<String, RoleBuckets> entry : groups.entrySet()) {
            RoleBuckets bucket = entry.getValue();

            try {
                // Outer "mixed" for inline part + attachments
                MimeMultipart mixed = new MimeMultipart("mixed");

                // Inner "related" for HTML + inline images
                MimeMultipart related = new MimeMultipart("related");

                // HTML
                MimeBodyPart html = new MimeBodyPart();
                html.setContent(htmlBody, "text/html; charset=" + UTF8);
                related.addBodyPart(html);

                // Inline logo (cid:logo)
                if (logoFile != null && logoFile.exists()) {
                    MimeBodyPart logo = new MimeBodyPart();
                    logo.setDataHandler(new DataHandler(new FileDataSource(logoFile)));
                    logo.setFileName(logoFile.getName());
                    logo.setHeader("Content-ID", "<logo>");
                    logo.setDisposition(MimeBodyPart.INLINE);
                    related.addBodyPart(logo);
                }

                // Wrap related into a body part and add FIRST so body renders
                MimeBodyPart relatedPart = new MimeBodyPart();
                relatedPart.setContent(related);
                mixed.addBodyPart(relatedPart);

                // Attachment: Extent report (also give it a CID so the button can refer to it)
                if (reportFile != null && reportFile.exists()) {
                    try {
                        MimeBodyPart att = new MimeBodyPart();
                        att.attachFile(reportFile);
                        att.setFileName(reportFile.getName());
                        att.setDisposition(MimeBodyPart.ATTACHMENT);
                        att.setHeader("Content-ID", "<report>"); // allows href="cid:report"
                        mixed.addBodyPart(att);
                    } catch (IOException io) {
                        LOG.warn("Report file found but could not be attached: {}", reportFile.getAbsolutePath(), io);
                    }
                } else if (reportFile != null) {
                    LOG.warn("Report file not found for attachment: {}", reportFile.getAbsolutePath());
                }

                // Message
                MimeMessage msg = new MimeMessage(session);
                setFrom(msg, fromEmail, fromName);
                bucket.addToMessage(msg);
                msg.setSubject(subject, UTF8);
                msg.setSentDate(new Date());
                msg.setContent(mixed);

                Transport.send(msg);
                LOG.info("Email sent to domain '{}' (to={}, cc={}, bcc={})",
                        entry.getKey(), bucket.to.size(), bucket.cc.size(), bucket.bcc.size());

            } catch (SendFailedException groupedFailure) {
                LOG.warn("Domain group send failed for {}. Falling back to per-recipient sends.",
                        entry.getKey(), groupedFailure);

                for (RoleBuckets.Entry e : bucket.entries()) {
                    try {
                        MimeMultipart mixed = new MimeMultipart("mixed");
                        MimeMultipart related = new MimeMultipart("related");

                        MimeBodyPart html = new MimeBodyPart();
                        html.setContent(htmlBody, "text/html; charset=" + UTF8);
                        related.addBodyPart(html);

                        if (logoFile != null && logoFile.exists()) {
                            MimeBodyPart logo = new MimeBodyPart();
                            logo.setDataHandler(new DataHandler(new FileDataSource(logoFile)));
                            logo.setFileName(logoFile.getName());
                            logo.setHeader("Content-ID", "<logo>");
                            logo.setDisposition(MimeBodyPart.INLINE);
                            related.addBodyPart(logo);
                        }

                        MimeBodyPart relatedPart = new MimeBodyPart();
                        relatedPart.setContent(related);
                        mixed.addBodyPart(relatedPart);

                        if (reportFile != null && reportFile.exists()) {
                            try {
                                MimeBodyPart att = new MimeBodyPart();
                                att.attachFile(reportFile);
                                att.setFileName(reportFile.getName());
                                att.setDisposition(MimeBodyPart.ATTACHMENT);
                                att.setHeader("Content-ID", "<report>");
                                mixed.addBodyPart(att);
                            } catch (IOException io) {
                                LOG.warn("Report file found but could not be attached: {}", reportFile.getAbsolutePath(), io);
                            }
                        }

                        MimeMessage single = new MimeMessage(session);
                        setFrom(single, fromEmail, fromName);
                        single.setRecipient(e.role, e.address);
                        single.setSubject(subject, UTF8);
                        single.setSentDate(new Date());
                        single.setContent(mixed);

                        Transport.send(single);
                        LOG.info("Email sent to {}", e.address.toUnicodeString());
                    } catch (Exception ex) {
                        LOG.error("Failed to email {}", e.address.toUnicodeString(), ex);
                    }
                }
            } catch (Exception e) {
                LOG.error("Failed to send email to domain {}", entry.getKey(), e);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static void setFrom(MimeMessage msg, String email, String displayName) throws MessagingException {
        if (isBlank(email)) return;
        try {
            if (!isBlank(displayName)) {
                msg.setFrom(new InternetAddress(email, displayName));
                return;
            }
        } catch (UnsupportedEncodingException ignore) {
            LOG.warn("Display name '{}' could not be applied; using plain address.", displayName);
        }
        msg.setFrom(new InternetAddress(email));
    }

    /** Parse CSV of emails into unique, validated InternetAddress[]. */
    private static InternetAddress[] parseAddresses(String csv) {
        if (isBlank(csv)) return new InternetAddress[0];
        String[] tokens = csv.split("[,;\\s]+");
        Map<String, InternetAddress> dedup = new LinkedHashMap<>();
        for (String token : tokens) {
            String addr = token == null ? "" : token.trim();
            if (addr.isEmpty()) continue;
            try {
                InternetAddress ia = new InternetAddress(addr, true); // strict validation
                dedup.put(ia.getAddress().toLowerCase(Locale.ROOT), ia);
            } catch (AddressException ex) {
                LOG.warn("Skipping invalid email: {}", addr);
            }
        }
        return dedup.values().toArray(new InternetAddress[0]);
    }

    /** Bucket recipients by domain while keeping their roles. */
    private static Map<String, RoleBuckets> bucketByDomain(InternetAddress[] to,
                                                           InternetAddress[] cc,
                                                           InternetAddress[] bcc) {
        Map<String, RoleBuckets> out = new LinkedHashMap<>();
        for (InternetAddress a : to)  out.computeIfAbsent(domainOf(a), k -> new RoleBuckets()).add(Message.RecipientType.TO, a);
        for (InternetAddress a : cc)  out.computeIfAbsent(domainOf(a), k -> new RoleBuckets()).add(Message.RecipientType.CC, a);
        for (InternetAddress a : bcc) out.computeIfAbsent(domainOf(a), k -> new RoleBuckets()).add(Message.RecipientType.BCC, a);
        return out;
    }

    private static String domainOf(InternetAddress a) {
        String addr = a.getAddress();
        int at = (addr == null) ? -1 : addr.lastIndexOf('@');
        return (at > 0) ? addr.substring(at + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static String orDefault(String v, String d) { return isBlank(v) ? d : v; }
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (!isBlank(v)) return v;
        return null;
    }

    /** If email.report.url is set, fill in the report filename if needed. */
    private static String buildReportUrl(String raw, File reportFile) {
        if (isBlank(raw)) return null;
        if (reportFile == null) return raw.trim();
        String url = raw.trim();
        String name = reportFile.getName();
        if (url.contains("{file}")) return url.replace("{file}", name);
        if (url.endsWith("/")) return url + name;
        return url;
    }

    /** Resolve logo file: try configured path; if missing, attempt classpath 'email/logo.*'. */
    private static File resolveLogoFile(String configuredPath) {
        try {
            if (!isBlank(configuredPath)) {
                File f = new File(configuredPath.trim());
                if (f.exists() && f.isFile()) return f;
                LOG.warn("Configured logo path not found: {}", f.getAbsolutePath());
            }
            // classpath fallback
            String[] candidates = {"email/logo.png", "email/logo.jpg", "email/logo.jpeg"};
            for (String cp : candidates) {
                try (InputStream is = EmailSenderUtil.class.getClassLoader().getResourceAsStream(cp)) {
                    if (is != null) {
                        File tmp = Files.createTempFile("email-logo-", cp.substring(cp.lastIndexOf('.'))).toFile();
                        tmp.deleteOnExit();
                        try (OutputStream os = new FileOutputStream(tmp)) {
                            is.transferTo(os);
                        }
                        return tmp;
                    }
                }
            }
        } catch (IOException ioe) {
            LOG.warn("Failed to materialize classpath logo image.", ioe);
        }
        return null; // no logo available
    }

    // ---------------------------------------------------------------------
    // Role-aware container
    // ---------------------------------------------------------------------
    private static final class RoleBuckets {
        final List<InternetAddress> to  = new ArrayList<>();
        final List<InternetAddress> cc  = new ArrayList<>();
        final List<InternetAddress> bcc = new ArrayList<>();

        void add(Message.RecipientType role, InternetAddress a) {
            if (Message.RecipientType.TO.equals(role))       to.add(a);
            else if (Message.RecipientType.CC.equals(role))  cc.add(a);
            else if (Message.RecipientType.BCC.equals(role)) bcc.add(a);
            else to.add(a);
        }

        /** Add all recipients to a message with preserved roles. */
        void addToMessage(MimeMessage msg) throws MessagingException {
            if (!to.isEmpty())  msg.addRecipients(Message.RecipientType.TO,  to.toArray(new Address[0]));
            if (!cc.isEmpty())  msg.addRecipients(Message.RecipientType.CC,  cc.toArray(new Address[0]));
            if (!bcc.isEmpty()) msg.addRecipients(Message.RecipientType.BCC, bcc.toArray(new Address[0]));
        }

        /** Flatten into role/address entries (for fallback sends). */
        List<Entry> entries() {
            List<Entry> all = new ArrayList<>(to.size() + cc.size() + bcc.size());
            for (InternetAddress a : to)  all.add(new Entry(Message.RecipientType.TO,  a));
            for (InternetAddress a : cc)  all.add(new Entry(Message.RecipientType.CC,  a));
            for (InternetAddress a : bcc) all.add(new Entry(Message.RecipientType.BCC, a));
            return all;
        }

        static final class Entry {
            final Message.RecipientType role;
            final InternetAddress       address;
            Entry(Message.RecipientType role, InternetAddress address) {
                this.role = role; this.address = address;
            }
        }
    }

    // ---------------------------------------------------------------------
    // HTML template
    // ---------------------------------------------------------------------
    private static String buildHtmlContent(String projectName,
                                           int totalTests, int testsPassed, int testsFailed,
                                           boolean hasHostedUrl, String buttonHref,
                                           String containerMargin, String bodyPadding) {

        double passPct = totalTests == 0 ? 0 : (testsPassed * 100.0 / totalTests);
        double failPct = totalTests == 0 ? 0 : (testsFailed * 100.0 / totalTests);
        String executionDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String cta = hasHostedUrl
                ? """
                   <a class="btn" href="%s" target="_blank" rel="noopener">View Detailed Report</a>
                   """.formatted(buttonHref)
                : """
                   <a class="btn" href="%s" style="background: #0052cc; color: #ffffff; text-decoration: none;\s
                                                                   padding: 12px 20px; border-radius: 8px; display: inline-block;
                                                                   font-weight: 600; font-family: Arial, sans-serif;" download>View Detailed Report</a>
                   <div style="margin-top:8px;color:#888;font-size:12px;">
                     If the button doesn’t download in your email client, use the report attached below.
                   </div>
                   """.formatted(buttonHref);

        return """
        <!doctype html>
        <html>
          <body style="margin:0;padding:%s;background:#f6f7fb;font-family:Segoe UI,Roboto,Arial,sans-serif;color:#111;">
            <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"
                   style="max-width:860px;width:100%%;margin:%s;background:#fff;border-radius:12px;box-shadow:0 4px 18px rgba(0,0,0,.08);overflow:hidden;">
              <tr>
                <td style="padding:0;">
                  <div style="background:linear-gradient(90deg,#0ea5e9,#22c55e);padding:20px 24px;color:#fff;display:flex;align-items:center;justify-content:space-between;">
                    <div style="font-size:22px;font-weight:700;letter-spacing:.2px;">
                      Project: %s
                    </div>
                    <img src="cid:logo" alt="Logo" style="height:56px;max-width:220px;display:block;border:0;outline:none;margin-left:auto;">
                  </div>
                </td>
              </tr>

              <tr>
                <td style="padding:24px 24px 12px 24px;">
                  <p style="margin:0 0 12px 0;">Hi Team,</p>
                  <p style="margin:0 0 16px 0;">The automation execution has completed. Please find the summary below:</p>

                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="border-collapse:collapse;border:1px solid #e8e9ef;">
                    <thead>
                      <tr style="background:#f3f7ff;">
                        <th style="padding:12px;border-right:1px solid #e8e9ef;text-align:left;font-size:14px;">Execution Date</th>
                        <th style="padding:12px;border-right:1px solid #e8e9ef;text-align:left;font-size:14px;">Total Tests</th>
                        <th style="padding:12px;border-right:1px solid #e8e9ef;text-align:left;font-size:14px;color:#16a34a;">Passed</th>
                        <th style="padding:12px;text-align:left;font-size:14px;color:#dc2626;">Failed</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td style="padding:12px;border-top:1px solid #e8e9ef;border-right:1px solid #e8e9ef;">%s</td>
                        <td style="padding:12px;border-top:1px solid #e8e9ef;border-right:1px solid #e8e9ef;">%d</td>
                        <td style="padding:12px;border-top:1px solid #e8e9ef;border-right:1px solid #e8e9ef;color:#16a34a;">%d (%.2f%%)</td>
                        <td style="padding:12px;border-top:1px solid #e8e9ef;color:#dc2626;">%d (%.2f%%)</td>
                      </tr>
                    </tbody>
                  </table>

                  <div style="text-align:center;margin:22px 0 6px 0;">
                    %s
                  </div>

                  <p style="margin:12px 0 0 0;color:#666;">
                    This is an automated email from the %s.
                  </p>
                </td>
              </tr>
            </table>

            <style>
              .btn{
                background:#0ea5e9;
                color:#fff !important;
                text-decoration:none;
                padding:12px 18px;
                border-radius:8px;
                display:inline-block;
                font-weight:600;
              }
              .btn:hover{ filter:brightness(0.95); }
            </style>
          </body>
        </html>
        """.formatted(
                bodyPadding,
                containerMargin,
                projectName,
                executionDate, totalTests, testsPassed, passPct, testsFailed, failPct,
                cta,
                projectName
        );
    }
}
