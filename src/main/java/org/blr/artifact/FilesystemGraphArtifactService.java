package org.blr.artifact;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.blr.error.AppException;
import org.blr.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class FilesystemGraphArtifactService implements GraphArtifactService {

    @Override
    public GraphProjectLayout discoverAndValidate(Path codeGraphOutputRoot) {
        if (codeGraphOutputRoot == null || !Files.exists(codeGraphOutputRoot)) {
            throw new AppException(ErrorCode.GRAPH_VALIDATION_FAILED, "CodeGraph output root does not exist");
        }

        Path projectDirectory = discoverProjectDirectory(codeGraphOutputRoot);
        validateProjectDirectory(projectDirectory);

        long fileCount = countReadableFiles(projectDirectory);
        if (fileCount == 0) {
            throw new AppException(ErrorCode.GRAPH_VALIDATION_FAILED, "CodeGraph project directory is empty");
        }

        return new GraphProjectLayout(projectDirectory, toProjectSlug(projectDirectory), fileCount);
    }

    @Override
    public GraphArtifactResult packageAndGenerateManifest(GraphArtifactRequest request, GraphProjectLayout layout) {
        try {
            Files.createDirectories(request.artifactDirectory());

            String artifactBaseName = request.repositoryId() + "_" + shortCommit(request.commitSha());
            Path archivePath = request.artifactDirectory().resolve(artifactBaseName + ".tar.gz");
            Path manifestPath = request.artifactDirectory().resolve(artifactBaseName + ".manifest.json");

            int entryCount = writeTarGz(layout.projectDirectory(), layout.projectSlug(), archivePath);
            validateArchive(archivePath);

            long archiveSize = Files.size(archivePath);
            String archiveSha = sha256Hex(archivePath);

            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("buildId", request.buildId());
            manifest.put("repositoryId", request.repositoryId());
            manifest.put("commitSha", request.commitSha());
            manifest.put("projectSlug", layout.projectSlug());
            manifest.put("projectFileCount", layout.fileCount());
            manifest.put("archiveFile", archivePath.getFileName().toString());
            manifest.put("archiveSizeBytes", archiveSize);
            manifest.put("archiveSha256", archiveSha);
            manifest.put("archiveEntryCount", entryCount);
            manifest.put("generatedAt", Instant.now().toString());

            Files.writeString(manifestPath, toJson(manifest));

            return new GraphArtifactResult(
                archivePath,
                manifestPath,
                layout.projectSlug(),
                archiveSha,
                archiveSize,
                entryCount
            );
        } catch (IOException ex) {
            throw new AppException(
                ErrorCode.GRAPH_ARTIFACT_PACKAGING_FAILED,
                "Failed to package graph artifact: " + ex.getMessage()
            );
        }
    }

    private Path discoverProjectDirectory(Path outputRoot) {
        if (Files.isDirectory(outputRoot)) {
            try (Stream<Path> stream = Files.list(outputRoot)) {
                List<Path> directories = stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();

                if (!directories.isEmpty()) {
                    return directories.get(0);
                }
            } catch (IOException ex) {
                throw new AppException(ErrorCode.GRAPH_VALIDATION_FAILED, "Failed to discover project directory");
            }
        }

        return outputRoot;
    }

    private void validateProjectDirectory(Path projectDirectory) {
        if (!Files.exists(projectDirectory) || !Files.isDirectory(projectDirectory)) {
            throw new AppException(ErrorCode.GRAPH_VALIDATION_FAILED, "Project directory is missing");
        }

        if (!Files.isReadable(projectDirectory)) {
            throw new AppException(ErrorCode.GRAPH_VALIDATION_FAILED, "Project directory is not readable");
        }
    }

    private long countReadableFiles(Path projectDirectory) {
        try (Stream<Path> walk = Files.walk(projectDirectory)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(Files::isReadable)
                .count();
        } catch (IOException ex) {
            throw new AppException(ErrorCode.GRAPH_VALIDATION_FAILED, "Failed to inspect project directory");
        }
    }

    private int writeTarGz(Path sourceDirectory, String projectSlug, Path archivePath) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(sourceDirectory)) {
            files = walk
                .filter(Files::isRegularFile)
                .sorted()
                .toList();
        }

        try (OutputStream fileOut = Files.newOutputStream(archivePath);
             OutputStream gzipOut = new java.util.zip.GZIPOutputStream(fileOut);
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)) {

            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

            int count = 0;
            for (Path file : files) {
                Path relative = sourceDirectory.relativize(file);
                String entryName = projectSlug + "/" + relative.toString().replace('\\', '/');

                TarArchiveEntry entry = new TarArchiveEntry(file.toFile(), entryName);
                tarOut.putArchiveEntry(entry);
                Files.copy(file, tarOut);
                tarOut.closeArchiveEntry();
                count++;
            }

            tarOut.finish();
            return count;
        }
    }

    private void validateArchive(Path archivePath) {
        try (InputStream fileIn = Files.newInputStream(archivePath);
             InputStream gzipIn = new java.util.zip.GZIPInputStream(fileIn);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn)) {

            TarArchiveEntry entry;
            int entries = 0;
            while ((entry = tarIn.getNextEntry()) != null) {
                if (entry.isFile()) {
                    entries++;
                }
            }

            if (entries == 0) {
                throw new AppException(ErrorCode.GRAPH_ARTIFACT_PACKAGING_FAILED, "Generated archive is empty");
            }
        } catch (IOException ex) {
            throw new AppException(
                ErrorCode.GRAPH_ARTIFACT_PACKAGING_FAILED,
                "Failed to validate generated archive: " + ex.getMessage()
            );
        }
    }

    private String sha256Hex(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new AppException(
                ErrorCode.GRAPH_ARTIFACT_PACKAGING_FAILED,
                "Failed to compute archive digest: " + ex.getMessage()
            );
        }
    }

    private String shortCommit(String commitSha) {
        return commitSha.length() <= 12 ? commitSha : commitSha.substring(0, 12);
    }

    private String toProjectSlug(Path projectDirectory) {
        String raw = projectDirectory.getFileName() == null ? "codegraph-project" : projectDirectory.getFileName().toString();
        String slug = raw.trim().toLowerCase().replaceAll("[^a-z0-9._-]+", "-");
        if (slug.isBlank()) {
            return "codegraph-project";
        }
        return slug;
    }

    private String toJson(Map<String, Object> values) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        int index = 0;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            sb.append("  \"").append(escape(entry.getKey())).append("\": ");
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append("\"").append(escape(String.valueOf(value))).append("\"");
            }
            if (index < values.size() - 1) {
                sb.append(',');
            }
            sb.append("\n");
            index++;
        }
        sb.append("}\n");
        return sb.toString();
    }

    private String escape(String input) {
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
