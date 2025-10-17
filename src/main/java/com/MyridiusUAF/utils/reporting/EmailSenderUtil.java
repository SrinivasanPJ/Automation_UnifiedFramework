package com.MyridiusUAF.utils.reporting;

import com.MyridiusUAF.config.ConfigReader;
import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Enterprise-grade SMTP email sender for automation reports.
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Preserves TO/CC/BCC roles and groups sends by destination domain.</li>
 *   <li>Automatic connection fallback across ports (configured → 465/SSL → 25 → 587)</li>
 *   <li>Inline logo (CID) and HTML summary; attaches the Extent HTML report (CID: report).</li>
 *   <li>Strict address validation; de-duplication; robust timeouts.</li>
 *   <li>Credentials read from env/property; supports Base64-decoded password.</li>
 * </ul>
 */
public final class EmailSenderUtil {

    // ---------------------------------------------------------------------
    // Constants & static state
    // ---------------------------------------------------------------------

    private static final Logger LOG = LoggerFactory.getLogger(EmailSenderUtil.class);
    private static final String UTF8 = StandardCharsets.UTF_8.name();

    /**
     * Project label used in email body header when not configured.
     */
    private static final String PROJECT_NAME =
            Optional.ofNullable(ConfigReader.getProperty("email.body.projectName"))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .orElse("Myridius Unified Framework");

    /**
     * Last-used auth/from values, populated in {@link #sendTestResultEmail(int, int, int)}
     * so per-attempt Sessions created inside port-fallback can authenticate.
     */
    private static String LAST_USERNAME, LAST_PASSWORD, LAST_FROM_EMAIL;

    private EmailSenderUtil() { /* utility */ }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Sends a test summary email to configured recipients.
     * <p>
     * Features:
     * - Reads SMTP configuration and credentials.
     * - Groups recipients by domain and attempts grouped send with fallback.
     * - Adds inline logo and attaches extent report.
     *
     * @param totalTests  Total tests executed
     * @param testsPassed Number of passed tests
     * @param testsFailed Number of failed tests
     */
    public static void sendTestResultEmail(int totalTests, int testsPassed, int testsFailed) {

        // -------------------------------------------------
        // SMTP & Credentials Configuration
        // -------------------------------------------------
        SmtpConfig smtp = resolveSmtpConfig();
        final String host = smtp.host;
        final String port = smtp.port;
        final boolean starttls = smtp.starttls;
        final String username = smtp.username;
        final String password = smtp.password;
        final String fromEmail = smtp.fromEmail;

        final String fromName = ConfigReader.getProperty("email.display.name");

        LAST_USERNAME = username;
        LAST_PASSWORD = password;
        LAST_FROM_EMAIL = fromEmail;

        // -------------------------------------------------
        // Recipients
        // -------------------------------------------------
        final InternetAddress[] to = parseAddresses(ConfigReader.getProperty("email.to"));
        final InternetAddress[] cc = parseAddresses(ConfigReader.getProperty("email.cc"));
        final InternetAddress[] bcc = parseAddresses(ConfigReader.getProperty("email.bcc"));

        if (to.length + cc.length + bcc.length == 0) {
            LOG.warn("Email not sent: No recipients configured.");
            return;
        }

        // -------------------------------------------------
        // Subject and Report Details
        // -------------------------------------------------
        final String subjectTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        final String subject = orDefault(ConfigReader.getProperty("email.subject"),
                "Automation Test Execution Report") + " - " + subjectTime;

        final String reportPath = ExtentReportManager.INSTANCE.getReportPath();
        final File reportFile = isBlank(reportPath) ? null : new File(reportPath);

        // -------------------------------------------------
        // Button / CTA Logic
        // -------------------------------------------------
        final String rawReportUrl = ConfigReader.getProperty("email.report.url");
        final String reportUrl = buildReportUrl(rawReportUrl, reportFile);
        final boolean hasHostedUrl = !isBlank(reportUrl) && (reportUrl.startsWith("http://") || reportUrl.startsWith("https://"));
        final String buttonHref = hasHostedUrl ? reportUrl : "cid:report";

        // -------------------------------------------------
        // Inline Logo
        // -------------------------------------------------
        final File logoFile = resolveLogoFile(ConfigReader.getProperty("email.logo.path"));

        // -------------------------------------------------
        // Layout Configuration
        // -------------------------------------------------
        final String alignProp = orDefault(ConfigReader.getProperty("email.layout.align"), "center");
        final String containerMargin = "left".equalsIgnoreCase(alignProp) ? "0" : "0 auto";
        final String bodyPadding = orDefault(ConfigReader.getProperty("email.layout.bodyPadding"), "20px");

        // -------------------------------------------------
        // HTML Body Construction
        // -------------------------------------------------
        final String htmlBody = buildHtmlContent(
                PROJECT_NAME, totalTests, testsPassed, testsFailed,
                hasHostedUrl, buttonHref, containerMargin, bodyPadding
        );

        // -------------------------------------------------
        // Group Send by Domain → Fallback to Individual
        // -------------------------------------------------
        final Map<String, RoleBuckets> groups = bucketByDomain(to, cc, bcc);

        for (Map.Entry<String, RoleBuckets> entry : groups.entrySet()) {
            final RoleBuckets bucket = entry.getValue();

            try {
                // Attempt grouped send
                final List<SmtpProfile> profiles = candidateProfiles(host, port, starttls);
                final boolean success = sendWithPortFallback(
                        profiles,
                        session -> {
                            MimeMessage message = new MimeMessage(session);
                            setFrom(message, fromEmail, fromName);
                            bucket.addToMessage(message);
                            message.setSubject(subject, UTF8);
                            message.setSentDate(new Date());
                            message.setContent(composeBody(logoFile, htmlBody, reportFile));
                            return message;
                        },
                        "group:" + entry.getKey()
                );

                if (success) {
                    LOG.info("Email sent to domain '{}' (to={}, cc={}, bcc={})",
                            entry.getKey(), bucket.to.size(), bucket.cc.size(), bucket.bcc.size());
                    continue;
                }

            } catch (Exception e) {
                LOG.warn("Domain group send failed for {}. Falling back to individual sends.", entry.getKey(), e);

                // Fallback to per-recipient sends
                for (RoleBuckets.Entry recipient : bucket.entries()) {
                    try {
                        final List<SmtpProfile> profiles = candidateProfiles(host, port, starttls);
                        final boolean okSingle = sendWithPortFallback(
                                profiles,
                                session -> {
                                    MimeMessage single = new MimeMessage(session);
                                    setFrom(single, fromEmail, fromName);
                                    single.setRecipient(recipient.role, recipient.address);
                                    single.setSubject(subject, UTF8);
                                    single.setSentDate(new Date());
                                    single.setContent(composeBody(logoFile, htmlBody, reportFile));
                                    return single;
                                },
                                "single:" + recipient.address.toUnicodeString()
                        );

                        if (okSingle) {
                            LOG.info("Email sent to {}", recipient.address.toUnicodeString());
                        } else {
                            LOG.error("Failed to email {} after trying all ports.", recipient.address.toUnicodeString());
                        }

                    } catch (Exception ex) {
                        LOG.error("Failed to email {}", recipient.address.toUnicodeString(), ex);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Body / MIME composition
    // ---------------------------------------------------------------------

    /**
     * Builds the full MIME body:
     * <ul>
     *   <li>mixed → (related → HTML + inline logo) + attachment(Extent report)</li>
     *   <li>Attachment is given Content-ID "report" so button can use href="cid:report"</li>
     * </ul>
     */
    private static MimeMultipart composeBody(File logoFile, String htmlBody, File reportFile) throws Exception {
        // Outer container (mixed) for body + attachments
        MimeMultipart mixed = new MimeMultipart("mixed");

        // Related container keeps HTML + inline images together
        MimeMultipart related = new MimeMultipart("related");

        // HTML part
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

        // Extent report attachment (with CID "report")
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
        } else if (reportFile != null) {
            LOG.warn("Report file not found for attachment: {}", reportFile.getAbsolutePath());
        }

        return mixed;
    }

    // ---------------------------------------------------------------------
    // Addressing / grouping helpers
    // ---------------------------------------------------------------------

    /**
     * Sets From with optional display name; falls back to plain address on encoding errors.
     */
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

    /**
     * Parse CSV of emails into unique, strictly validated {@link InternetAddress} array.
     */
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

    /**
     * Bucket recipients by domain while keeping their roles.
     */
    private static Map<String, RoleBuckets> bucketByDomain(InternetAddress[] to,
                                                           InternetAddress[] cc,
                                                           InternetAddress[] bcc) {
        Map<String, RoleBuckets> out = new LinkedHashMap<>();
        for (InternetAddress a : to)
            out.computeIfAbsent(domainOf(a), k -> new RoleBuckets()).add(Message.RecipientType.TO, a);
        for (InternetAddress a : cc)
            out.computeIfAbsent(domainOf(a), k -> new RoleBuckets()).add(Message.RecipientType.CC, a);
        for (InternetAddress a : bcc)
            out.computeIfAbsent(domainOf(a), k -> new RoleBuckets()).add(Message.RecipientType.BCC, a);
        return out;
    }

    private static String domainOf(InternetAddress a) {
        String addr = a.getAddress();
        int at = (addr == null) ? -1 : addr.lastIndexOf('@');
        return (at > 0) ? addr.substring(at + 1).toLowerCase(Locale.ROOT) : "";
    }

    // ---------------------------------------------------------------------
    // SMTP fallback & Session helpers
    // ---------------------------------------------------------------------

    /**
     * Build candidate profiles: configured port first, then sensible fallbacks (465, 25, 587).
     */
    private static List<SmtpProfile> candidateProfiles(String host, String configuredPort, boolean starttlsDefault) {
        List<SmtpProfile> out = new ArrayList<>();
        int cfg = safeParsePort(configuredPort, 587);
        boolean cfgIsSsl = (cfg == 465);

        // Preferred profile
        out.add(new SmtpProfile(host, cfg, !cfgIsSsl && starttlsDefault, cfgIsSsl));

        // Allow override list like "smtp.fallback.ports=465,25"
        String csv = ConfigReader.getProperty("smtp.fallback.ports");
        List<Integer> ports = new ArrayList<>();
        if (csv != null && !csv.isBlank()) {
            for (String t : csv.split("[,\\s]+")) {
                Integer p = tryParsePort(t.trim());
                if (p != null && p != cfg) ports.add(p);
            }
        } else {
            if (cfg != 465) ports.add(465);
            if (cfg != 25) ports.add(25);
            if (cfg != 587) ports.add(587);
        }

        for (int p : ports) {
            if (p == 465) out.add(new SmtpProfile(host, 465, false, true));
            else if (p == 587) out.add(new SmtpProfile(host, 587, true, false));
            else out.add(new SmtpProfile(host, p, false, false));
        }
        return out;
    }

    /**
     * Create a {@link Session} for a given profile and the last-read credentials.
     */
    private static Session newSession(SmtpProfile p, String username, String password, String fromEmail) {
        Properties props = new Properties();
        props.put("mail.smtp.host", p.host);
        props.put("mail.smtp.port", Integer.toString(p.port));
        props.put("mail.smtp.auth", (username != null && !username.isBlank()) ? "true" : "false");
        props.put("mail.smtp.starttls.enable", Boolean.toString(p.starttls));
        props.put("mail.smtp.ssl.enable", Boolean.toString(p.ssl));
        props.put("mail.smtp.ssl.trust", p.host);
        props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        props.put("mail.smtp.sendpartial", "true");
        props.put("mail.smtp.connectiontimeout", "20000");
        props.put("mail.smtp.timeout", "20000");
        props.put("mail.smtp.writetimeout", "20000");
        if (!isBlank(fromEmail)) props.put("mail.smtp.from", fromEmail);

        return Session.getInstance(props,
                (username == null || username.isBlank()) ? null :
                        new Authenticator() {
                            @Override
                            protected PasswordAuthentication getPasswordAuthentication() {
                                return new PasswordAuthentication(username, password);
                            }
                        });
    }

    /**
     * Attempt to send the message across multiple SMTP profiles (ports/TLS),
     * returning true on first success. Connection-like failures are retried;
     * other {@link MessagingException}s are surfaced.
     */
    private static boolean sendWithPortFallback(
            List<SmtpProfile> profiles, MsgFactory factory, String logTag) {
        for (SmtpProfile prof : profiles) {
            try {
                Session s = newSession(prof, LAST_USERNAME, LAST_PASSWORD, LAST_FROM_EMAIL);
                MimeMessage m = factory.build(s);
                Transport.send(m);
                LOG.info("Email sent via {}:{} (starttls={}, ssl={}) [{}]",
                        prof.host, prof.port, prof.starttls, prof.ssl, logTag);
                return true;
            } catch (com.sun.mail.util.MailConnectException ce) {
                LOG.warn("SMTP connect failed on {}:{} -> {} [{}]",
                        prof.host, prof.port, clean(ce.getMessage()), logTag);
                continue;
            } catch (MessagingException me) {
                if (isConnectLike(me)) {
                    LOG.warn("SMTP connect failed on {}:{} -> {} [{}]",
                            prof.host, prof.port, clean(me.getMessage()), logTag);
                    continue;
                }
                throw new RuntimeException(me);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        return false;
    }

    /**
     * Heuristic: treat timeouts/connect errors as retryable.
     */
    private static boolean isConnectLike(MessagingException me) {
        Throwable c = me;
        while (c != null) {
            if (c instanceof com.sun.mail.util.MailConnectException) return true;
            if (c instanceof java.net.SocketTimeoutException) return true;
            if (c instanceof java.net.ConnectException) return true;
            c = c.getCause();
        }
        String msg = me.getMessage() == null ? "" : me.getMessage().toLowerCase(Locale.ROOT);
        return msg.contains("couldn't connect") || msg.contains("could not connect")
                || msg.contains("connection timed out") || msg.contains("timed out");
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\n', ' ').trim();
    }

    private static String orDefault(String v, String d) {
        return isBlank(v) ? d : v;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ---------------------------------------------------------------------
    // Misc helpers (I/O, strings, logo, link)
    // ---------------------------------------------------------------------

    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (!isBlank(v)) return v;
        return null;
    }

    /**
     * If email.report.url is set, fill in the report filename if needed.
     */
    private static String buildReportUrl(String raw, File reportFile) {
        if (isBlank(raw)) return null;
        if (reportFile == null) return raw.trim();
        String url = raw.trim();
        String name = reportFile.getName();
        if (url.contains("{file}")) return url.replace("{file}", name);
        if (url.endsWith("/")) return url + name;
        return url;
    }

    /**
     * Resolve logo file: try configured path; if missing, attempt classpath 'email/logo.*'.
     */
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

    private static Integer tryParsePort(String s) {
        try {
            int p = Integer.parseInt(s);
            return (p > 0 && p < 65536) ? p : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private static int safeParsePort(String s, int def) {
        Integer p = tryParsePort(s);
        return p == null ? def : p;
    }

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
                <a class="btn" href="%s" style="background: #0052cc; color: #ffffff; text-decoration: none;
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

    private static SmtpConfig resolveSmtpConfig() {
        String provider = orDefault(ConfigReader.getProperty("smtp.provider"), "gmail").trim().toLowerCase();

        switch (provider) {
            case "outlook":
                return new SmtpConfig(
                        "smtp.office365.com",
                        "587",
                        true,
                        firstNonBlank(ConfigReader.getProperty("smtp.username")),
                        firstNonBlank(System.getenv("OUTLOOK_PASSWORD"),
                                ConfigReader.getDecryptedProperty("smtp.password"),
                                ConfigReader.getProperty("smtp.password")),
                        ConfigReader.getProperty("email.from")
                );

            case "gmail":
            default:
                return new SmtpConfig(
                        "smtp.gmail.com",
                        "587",
                        true,
                        firstNonBlank(ConfigReader.getProperty("smtp.username")),
                        firstNonBlank(System.getenv("SMTP_PASSWORD"),
                                ConfigReader.getDecryptedProperty("smtp.password"),
                                ConfigReader.getProperty("smtp.password")),
                        ConfigReader.getProperty("email.from")
                );
        }
    }

    // ---------------------------------------------------------------------
    // Role-aware container
    // ---------------------------------------------------------------------

    @FunctionalInterface
    private interface MsgFactory {
        MimeMessage build(Session s) throws Exception;
    }

    // ---------------------------------------------------------------------
    // HTML template
    // ---------------------------------------------------------------------

    /**
     * SMTP connection profile for a specific port and TLS mode.
     */
    private static final class SmtpProfile {
        final String host;
        final int port;
        final boolean starttls;
        final boolean ssl;

        SmtpProfile(String host, int port, boolean starttls, boolean ssl) {
            this.host = host;
            this.port = port;
            this.starttls = starttls;
            this.ssl = ssl;
        }
    }

    /**
     * Maintains recipients by role and domain; preserves roles when applied to a message.
     */
    private static final class RoleBuckets {
        final List<InternetAddress> to = new ArrayList<>();
        final List<InternetAddress> cc = new ArrayList<>();
        final List<InternetAddress> bcc = new ArrayList<>();

        void add(Message.RecipientType role, InternetAddress a) {
            if (Message.RecipientType.TO.equals(role)) to.add(a);
            else if (Message.RecipientType.CC.equals(role)) cc.add(a);
            else if (Message.RecipientType.BCC.equals(role)) bcc.add(a);
            else to.add(a);
        }

        /**
         * Add all recipients to a message with preserved roles.
         */
        void addToMessage(MimeMessage msg) throws MessagingException {
            if (!to.isEmpty()) msg.addRecipients(Message.RecipientType.TO, to.toArray(new Address[0]));
            if (!cc.isEmpty()) msg.addRecipients(Message.RecipientType.CC, cc.toArray(new Address[0]));
            if (!bcc.isEmpty()) msg.addRecipients(Message.RecipientType.BCC, bcc.toArray(new Address[0]));
        }

        /**
         * Flatten into role/address entries (for per-recipient fallback).
         */
        List<Entry> entries() {
            List<Entry> all = new ArrayList<>(to.size() + cc.size() + bcc.size());
            for (InternetAddress a : to) all.add(new Entry(Message.RecipientType.TO, a));
            for (InternetAddress a : cc) all.add(new Entry(Message.RecipientType.CC, a));
            for (InternetAddress a : bcc) all.add(new Entry(Message.RecipientType.BCC, a));
            return all;
        }

        static final class Entry {
            final Message.RecipientType role;
            final InternetAddress address;

            Entry(Message.RecipientType role, InternetAddress address) {
                this.role = role;
                this.address = address;
            }
        }
    }

    private static class SmtpConfig {
        final String host;
        final String port;
        final boolean starttls;
        final String username;
        final String password;
        final String fromEmail;

        SmtpConfig(String host, String port, boolean starttls, String username, String password, String fromEmail) {
            this.host = host;
            this.port = port;
            this.starttls = starttls;
            this.username = username;
            this.password = password;
            this.fromEmail = fromEmail;
        }
    }
}
