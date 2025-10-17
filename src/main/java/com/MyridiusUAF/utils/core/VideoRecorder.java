package com.MyridiusUAF.utils.core;

import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.reporting.LogUtil;
import org.openqa.selenium.WebDriver;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin, cross-platform FFmpeg screen recorder for Selenium runs.
 * - Starts after AUT is first shown (call startOnceForTest(...) right after driver.get(url)).
 * - Stops in @AfterMethod for PASS/FAIL/SKIP.
 * - Writes MP4 files under Recordings/YYYY-MM-DD/.
 * <p>
 * Devices:
 * Windows: gdigrab (screen)
 * macOS  : avfoundation (screen index)
 * Linux  : x11grab (:0.0)
 */
public enum VideoRecorder {
    INSTANCE;

    private static final String CFG_ENABLED = "recording.enabled";
    private static final String CFG_MODE = "recording.mode"; // per_test | per_run
    private static final String CFG_FFMPEG = "recording.ffmpeg.path";
    private static final String CFG_OUTDIR = "recording.output.dir";
    private static final String CFG_FPS = "recording.fps";
    private static final String CFG_PRESET = "recording.preset";
    private static final String CFG_CRF = "recording.crf";
    private static final String CFG_CAPTURE = "recording.capture"; // screen | window (window is experimental)
    private static final String CFG_MAC_IDX = "recording.macos.screen.index";
    private static final String CFG_X11 = "recording.linux.display";
    private static final String CFG_STOP_MS = "recording.stop.timeout.ms";

    private final Map<String, Proc> running = new ConcurrentHashMap<>();
    private volatile Proc runLevel; // for per_run mode

    private static boolean isExecutableOnPath(String exe) {
        try {
            new ProcessBuilder(exe, "-version").redirectErrorStream(true).start().destroy();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ------------- Public API -------------

    private static Thread gobble(String name, InputStream in) {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                while (br.readLine() != null) { /* discard */ }
            } catch (IOException ignored) {
            }
        }, name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void joinQuietly(Thread t) {
        try {
            if (t != null) t.join(4000);
        } catch (InterruptedException ignored) {
        }
    }

    private static void closeQuietly(OutputStream os) {
        try {
            if (os != null) os.close();
        } catch (IOException ignored) {
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    // ------------- Core spawning/stop -------------

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    private static boolean headless() {
        String h = ConfigReader.getProperty("headless.mode", "false");
        return "true".equalsIgnoreCase(h);
    }

    private static boolean enabled() {
        return "true".equalsIgnoreCase(ConfigReader.getProperty(CFG_ENABLED, "false"));
    }

    // ------------- Command builders -------------

    private static String mode() {
        return ConfigReader.getProperty(CFG_MODE, "per_test");
    }

    private static String opt(String k, String d) {
        return ConfigReader.getProperty(k, d);
    }

    // ------------- Utils -------------

    /**
     * Start (once) for a test when the AUT is visible (call right after driver.get).
     */
    public void startOnceForTest(String testKey, WebDriver driver) {
        if (!enabled() || headless()) return;
        if ("per_run".equalsIgnoreCase(mode())) {
            startRunIfNeeded();
            return;
        }
        running.computeIfAbsent(testKey, k -> spawn(k, null));
    }

    /**
     * Stop for a test; idempotent. Call in @AfterMethod(alwaysRun = true).
     */
    public void stopIfRunning(String testKey) {
        Proc proc = running.remove(testKey);
        if (proc != null) stop(proc);
    }

    /**
     * For run-level recording if desired (mode=per_run).
     */
    public void startRunIfNeeded() {
        if (!enabled() || headless()) return;
        if (!"per run".equalsIgnoreCase(mode())) return;
        if (runLevel == null) {
            synchronized (this) {
                if (runLevel == null) runLevel = spawn("RUN", null);
            }
        }
    }

    /**
     * Stop run-level recording if present (call in IExecutionListener/Reporter at the very end).
     */
    public void stopRunIfRunning() {
        if (!"per_run".equalsIgnoreCase(mode())) return;
        Proc p = runLevel;
        runLevel = null;
        if (p != null) stop(p);
    }

    private Proc spawn(String key, Rectangle region) {
        try {
            String ffmpeg = opt(CFG_FFMPEG, "ffmpeg");
            if (!isExecutableOnPath(ffmpeg) && !Files.isExecutable(Path.of(ffmpeg))) {
                LogUtil.warn(VideoRecorder.class, "FFmpeg not found. Set 'recording.ffmpeg.path' to the full exe path " +
                        "or restart IDE so PATH is refreshed. Tried: " + ffmpeg);
                return null;
            }
            List<String> cmd = buildCommand(ffmpeg, region);
            Path out = outFile(key);

            Files.createDirectories(out.getParent());

            List<String> full = new ArrayList<>(cmd);
            full.addAll(List.of(
                    "-y",
                    "-vcodec", "libx264",
                    "-preset", opt(CFG_PRESET, "veryfast"),
                    "-crf", opt(CFG_CRF, "28"),
                    "-pix_fmt", "yuv420p",
                    out.toAbsolutePath().toString()
            ));

            LogUtil.log(VideoRecorder.class, "Starting FFmpeg: " + String.join(" ", full));

            ProcessBuilder pb = new ProcessBuilder(full);
            pb.redirectErrorStream(false);
            Process p = pb.start();

            // Gobble streams so ffmpeg never blocks
            Thread outG = gobble("ffmpeg-out", p.getInputStream());
            Thread errG = gobble("ffmpeg-err", p.getErrorStream());

            return new Proc(p, p.getOutputStream(), outG, errG, out);

        } catch (Exception e) {
            LogUtil.warn(VideoRecorder.class, "Failed to start recorder: " + e.getMessage());
            return null;
        }
    }

    private void stop(Proc proc) {
        final int timeoutMs = Integer.parseInt(opt(CFG_STOP_MS, "6000"));
        try {
            // Ask ffmpeg to finish cleanly
            if (proc.stdin != null) {
                proc.stdin.write('q');
                proc.stdin.flush();
                closeQuietly(proc.stdin);
            }

            long start = System.currentTimeMillis();
            while (proc.p.isAlive() && (System.currentTimeMillis() - start) < timeoutMs) {
                Thread.sleep(120);
            }
            if (proc.p.isAlive()) {
                LogUtil.warn(VideoRecorder.class, "FFmpeg not exiting, killing process...");
                proc.p.destroyForcibly();
            }
        } catch (Exception e) {
            try {
                proc.p.destroyForcibly();
            } catch (Exception ignored) {
            }
        } finally {
            joinQuietly(proc.stdout);
            joinQuietly(proc.stderr);
            LogUtil.log(VideoRecorder.class, "Recording saved: " + proc.file.toAbsolutePath());
        }
    }

    private List<String> buildCommand(String ffmpeg, Rectangle region) throws Exception {
        String fps = opt(CFG_FPS, "15");
        if (isWindows()) {
            // gdigrab: full desktop capture
            List<String> args = new ArrayList<>(List.of(
                    ffmpeg, "-f", "gdigrab",
                    "-framerate", fps,
                    "-i", "desktop"
            ));
            // region crop (optional)
            if (region != null) {
                // crop via filter; keeping simple we leave full-screen by default
            }
            return args;
        }
        if (isMac()) {
            // avfoundation: screen index; audio none
            String idx = opt(CFG_MAC_IDX, "1");
            return List.of(
                    ffmpeg, "-f", "avfoundation",
                    "-framerate", fps,
                    "-i", idx + ":none"
            );
        }
        // Linux (x11grab)
        String display = opt(CFG_X11, ":0.0");
        Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        String size = d.width + "x" + d.height;
        return List.of(
                ffmpeg, "-f", "x11grab",
                "-framerate", fps,
                "-video_size", size,
                "-i", display
        );
    }

    private Path outFile(String key) {
        String base = opt(CFG_OUTDIR, "Recordings"); // e.g., "Recordings"
        //String day = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String safe = key.replaceAll("[^a-zA-Z0-9_.#-]", "_"); // sanitize file name

        Path dir = Paths.get(base); // no day subfolder
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }

        return dir.resolve(ts + "_" + safe + ".mp4"); // Recordings/<ts>_<key>.mp4
    }

    private static record Proc(Process p, OutputStream stdin, Thread stdout, Thread stderr, Path file) {
    }
}
