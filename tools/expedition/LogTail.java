package me.lovkar.voyagerexpedition;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The server's own log, read back from where the last read stopped: Voyager's "[Voyager] ..." lines,
 * in order, with the prefix and everything before it cut off.
 *
 * <p>The probe cannot hang an appender on log4j (log4j-core is not on its classpath), and it does
 * not need to: the dedicated server writes logs/latest.log in its working directory, and the probe
 * reads the new bytes on its own schedule. Lines are split on the newline byte, so a multi-byte
 * character cut in half by a read waits for its other half.</p>
 */
final class LogTail {

    private static final String PREFIX = "[Voyager] ";

    private final Path file;
    private long offset = 0L;
    private final ByteArrayOutputStream partial = new ByteArrayOutputStream();
    private final List<String> lines = new ArrayList<>();

    LogTail(final Path file) {
        this.file = file;
    }

    /** Reads whatever the server wrote since the last call. Quiet when the file is not there (yet). */
    void poll() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            final long length = raf.length();
            if (length < offset) {
                // a new file under the old name: start over
                offset = 0L;
                partial.reset();
            }
            if (length == offset) {
                return;
            }
            final byte[] chunk = new byte[(int) Math.min(Integer.MAX_VALUE - 16, length - offset)];
            raf.seek(offset);
            raf.readFully(chunk);
            offset += chunk.length;
            int start = 0;
            for (int i = 0; i < chunk.length; i++) {
                if (chunk[i] == '\n') {
                    partial.write(chunk, start, i - start);
                    take(partial.toString(StandardCharsets.UTF_8));
                    partial.reset();
                    start = i + 1;
                }
            }
            partial.write(chunk, start, chunk.length - start);
        } catch (final IOException ignored) {
            // the next poll tries again
        }
    }

    private void take(final String line) {
        final int at = line.indexOf(PREFIX);
        if (at >= 0) {
            lines.add(line.substring(at + PREFIX.length()).trim());
        }
    }

    /** Voyager's lines so far (the live list: read it, do not keep it across polls). */
    List<String> lines() {
        return lines;
    }

    int size() {
        return lines.size();
    }

    /** Index of the first line at or after {@code from} that contains the text, or -1. */
    int indexOf(final String needle, final int from) {
        for (int i = Math.max(0, from); i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    /** Index of the first line at or after {@code from} that the pattern finds something in, or -1. */
    int indexOf(final Pattern pattern, final int from) {
        for (int i = Math.max(0, from); i < lines.size(); i++) {
            if (pattern.matcher(lines.get(i)).find()) {
                return i;
            }
        }
        return -1;
    }

    boolean any(final String needle) {
        return indexOf(needle, 0) >= 0;
    }

    int count(final String needle) {
        int n = 0;
        for (final String line : lines) {
            if (line.contains(needle)) {
                n++;
            }
        }
        return n;
    }

    /** The first line at or after {@code from} that the pattern finds something in, or null. */
    Matcher find(final Pattern pattern, final int from) {
        for (int i = Math.max(0, from); i < lines.size(); i++) {
            final Matcher m = pattern.matcher(lines.get(i));
            if (m.find()) {
                return m;
            }
        }
        return null;
    }

    /** Every line the pattern finds something in, as matchers. */
    List<Matcher> findAll(final Pattern pattern) {
        final List<Matcher> out = new ArrayList<>();
        for (final String line : lines) {
            final Matcher m = pattern.matcher(line);
            if (m.find()) {
                out.add(m);
            }
        }
        return out;
    }
}
