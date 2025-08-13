package com.MyridiusUAF.utils.core;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Utilities for awaiting filesystem changes.
 *
 * <p>Uses {@link WatchService} for efficient waiting, with a safe fallback to
 * lightweight polling when directory watching isn't available (e.g., some
 * network filesystems) or becomes invalid during the wait.</p>
 */
public final class FileAwait {

    private FileAwait() { /* utility */ }

    /**
     * Blocks until the given {@code file} exists or the {@code timeout} elapses.
     * <ul>
     *   <li>Returns immediately if the file already exists.</li>
     *   <li>Waits on directory change notifications (CREATE/MODIFY) for the file name.</li>
     *   <li>Falls back to short-interval polling if watching is unsupported or fails.</li>
     *   <li>Honors the overall timeout even if the directory is noisy (hard deadline).</li>
     * </ul>
     *
     * @param file    path to the file to await (absolute or relative)
     * @param timeout maximum time to wait; zero/negative ⇒ immediate existence check
     * @return {@code true} if the file exists before the deadline; otherwise {@code false}
     * @throws NullPointerException if {@code file} or {@code timeout} is {@code null}
     */
    public static boolean waitForExistence(Path file, Duration timeout) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(timeout, "timeout");

        // Fast path & trivial timeouts
        if (Files.exists(file) || timeout.isZero() || timeout.isNegative()) {
            return Files.exists(file);
        }

        Path dir = file.getParent();
        if (dir == null) {
            dir = Paths.get(".");
        }

        // If the parent directory isn't there, there's nothing to watch—poll instead.
        if (!Files.isDirectory(dir)) {
            return pollUntilDeadline(file, timeout);
        }

        final String targetName = file.getFileName().toString();
        final long deadline = System.nanoTime() + timeout.toNanos();

        try (WatchService ws = FileSystems.getDefault().newWatchService()) {
            dir.register(ws,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);

            while (true) {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0L) {
                    return Files.exists(file);
                }

                WatchKey key = ws.poll(remainingNanos, TimeUnit.NANOSECONDS);
                if (key == null) {
                    // Timed out waiting for events
                    return Files.exists(file);
                }

                for (WatchEvent<?> ev : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = ev.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Path changed = ((WatchEvent<Path>) ev).context();
                    if (changed != null
                            && targetName.equals(changed.getFileName().toString())
                            && Files.exists(file)) {
                        key.reset();
                        return true;
                    }
                }

                // Directory became inaccessible? Degrade to polling for the remainder.
                if (!key.reset()) {
                    long rem = Math.max(0L, deadline - System.nanoTime());
                    return pollUntilDeadline(file, Duration.ofNanos(rem));
                }

                // Defensive check between event loops
                if (Files.exists(file)) {
                    return true;
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return Files.exists(file);
        } catch (IOException ioe) {
            // Platform may not support watching here—poll for the remainder.
            long rem = Math.max(0L, deadline - System.nanoTime());
            return pollUntilDeadline(file, Duration.ofNanos(rem));
        }
    }

    /**
     * Lightweight polling fallback that honors the remaining deadline.
     */
    private static boolean pollUntilDeadline(Path file, Duration remaining) {
        final long deadline = System.nanoTime() + Math.max(0L, remaining.toNanos());
        try {
            while (System.nanoTime() < deadline) {
                if (Files.exists(file)) return true;
                Thread.sleep(50);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return Files.exists(file);
        }
        return Files.exists(file);
    }
}
