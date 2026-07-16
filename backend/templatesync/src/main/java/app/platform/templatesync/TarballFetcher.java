package app.platform.templatesync;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * GitHub tarball fetcher with sandboxed extraction (3.11). Guards: 50 MiB decompressed total,
 * 5 MiB per file, 5000 entries, no `..`/absolute paths, no links or special entries — template
 * authors are semi-trusted. Interim auth: optional PAT env; the GitHub App (3.07) replaces it.
 */
@Service
public class TarballFetcher {

    private static final Logger log = LoggerFactory.getLogger(TarballFetcher.class);
    private static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_ENTRIES = 5000;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final String token;

    public TarballFetcher(@Value("${platform.github.token:${PLATFORM_GITHUB_TOKEN:}}") String token) {
        this.token = token;
    }

    public record ExtractedRepo(Path baseDir) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            try (var walk = Files.walk(baseDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    public ExtractedRepo fetch(String orgRepo, String ref) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + orgRepo + "/tarball/" + ref))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "platform-sync");
        if (!token.isBlank()) {
            request.header("Authorization", "Bearer " + token);
        }
        HttpResponse<InputStream> response =
                http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("GitHub tarball fetch failed: HTTP " + response.statusCode()
                    + " for " + orgRepo + "@" + ref + (token.isBlank() ? " (no token — private repo?)" : ""));
        }

        Path base = Files.createTempDirectory("gh-" + UUID.randomUUID());
        long total = 0;
        int entries = 0;
        try (TarArchiveInputStream tar = new TarArchiveInputStream(new GZIPInputStream(response.body()))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new IOException("Tarball exceeds " + MAX_ENTRIES + " entries");
                }
                String name = entry.getName();
                if (name.contains("..") || name.startsWith("/")) {
                    throw new IOException("Rejected path traversal entry: " + name);
                }
                if (entry.isSymbolicLink() || entry.isLink() || !(entry.isDirectory() || entry.isFile())) {
                    throw new IOException("Rejected non-regular entry: " + name);
                }
                Path target = base.resolve(name).normalize();
                if (!target.startsWith(base)) {
                    throw new IOException("Rejected escaping entry: " + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                if (entry.getSize() > MAX_FILE_BYTES) {
                    throw new IOException("File too large: " + name);
                }
                Files.createDirectories(target.getParent());
                long written = Files.copy(tar, target);
                total += written;
                if (total > MAX_TOTAL_BYTES) {
                    throw new IOException("Tarball decompressed size exceeds 50 MiB (bomb defense)");
                }
            }
        } catch (IOException e) {
            new ExtractedRepo(base).close();
            throw e;
        }
        log.info("Extracted {}@{}: {} entries, {} bytes", orgRepo, ref, entries, total);
        return new ExtractedRepo(base);
    }
}
