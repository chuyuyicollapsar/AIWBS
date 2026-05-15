package example.aiwbs.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import example.aiwbs.model.AppState;
import example.aiwbs.model.Book;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class PackageStore {
    private static final String FORMAT = "aiwbs-package";
    private static final int FORMAT_VERSION = 1;
    private static final long MAX_ENTRY_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_PACKAGE_BYTES = 512L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 20_000;
    private static final Pattern BOOK_ENTRY = Pattern.compile("books/[^/]+\\.json");
    private static final Pattern COVER_ENTRY = Pattern.compile("covers/[^/]+\\.(png|jpg|jpeg|webp)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SESSION_ENTRY = Pattern.compile("ai/books/[^/]+/sessions/[^/]+\\.json");

    private final Path dataDir;
    private final Path booksDir;
    private final Path aiBooksDir;
    private final BookStore bookStore;
    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    PackageStore(Path dataDir, BookStore bookStore) {
        this.dataDir = dataDir;
        this.booksDir = dataDir.resolve("books");
        this.aiBooksDir = dataDir.resolve("ai").resolve("books");
        this.bookStore = bookStore;
    }

    PackageTransferResult exportPackage(AppState state, Path target) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }

        ManifestData manifest = ManifestData.from(state, this::countSessions);
        int sessionCount = 0;
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target), StandardCharsets.UTF_8)) {
            addText(zip, "manifest.json", gson.toJson(manifest));
            addText(zip, "settings.json", gson.toJson(PackageSettingsData.from(state)));
            for (Book book : state.getBooks()) {
                String bookId = safe(book.getId());
                if (bookId.isBlank()) continue;
                addFile(zip, booksDir.resolve(bookId + ".json"), "books/" + bookId + ".json");

                String coverPath = safe(book.getCoverPath()).replace('\\', '/');
                if (COVER_ENTRY.matcher(coverPath).matches()) {
                    addFile(zip, dataDir.resolve(coverPath).normalize(), coverPath);
                }

                sessionCount += addSessions(zip, bookId);
            }
        }
        return new PackageTransferResult(
                state.getBooks().size(),
                sessionCount,
                state.getBooks().stream().map(Book::getId).toList(),
                state.getBooks().stream().map(Book::getName).toList()
        );
    }

    PackageTransferResult importPackage(AppState state, Path source) throws IOException {
        Path tempDir = Files.createTempDirectory("aiwbs-import-");
        try {
            extract(source, tempDir);
            ManifestData manifest = readManifest(tempDir);
            List<Path> bookFiles = orderedBookFiles(tempDir, manifest);
            if (bookFiles.isEmpty()) {
                throw new IOException("Package does not contain any books.");
            }
            validatePackagePayload(tempDir, bookFiles);

            Set<String> liveIds = new HashSet<>();
            for (Book book : state.getBooks()) {
                if (book.getId() != null && !book.getId().isBlank()) {
                    liveIds.add(book.getId());
                }
            }

            List<Book> importedBooks = new ArrayList<>();
            List<String> importedIds = new ArrayList<>();
            List<String> importedNames = new ArrayList<>();
            int sessionCount = 0;

            for (Path bookFile : bookFiles) {
                Book book = bookStore.load(bookFile);
                if (book == null) continue;

                String packageBookId = fileId(bookFile);
                String originalBookId = safe(book.getId()).isBlank() ? packageBookId : book.getId();
                String targetBookId = liveIds.add(originalBookId) ? originalBookId : newUniqueId(liveIds);
                book.setId(targetBookId);

                Path coverSource = resolvePackageCover(tempDir, book.getCoverPath());
                if (coverSource != null) {
                    book.setCoverPath(bookStore.importCover(book, coverSource));
                } else {
                    book.setCoverPath("");
                }

                sessionCount += importSessions(tempDir, packageBookId, targetBookId);
                importedBooks.add(book);
                importedIds.add(book.getId());
                importedNames.add(book.getName());
            }

            if (importedBooks.isEmpty()) {
                throw new IOException("Package books could not be read.");
            }

            state.getBooks().addAll(importedBooks);
            return new PackageTransferResult(importedBooks.size(), sessionCount, importedIds, importedNames);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private int addSessions(ZipOutputStream zip, String bookId) throws IOException {
        Path sessionsDir = aiBooksDir.resolve(bookId).resolve("sessions");
        if (!Files.isDirectory(sessionsDir)) return 0;

        int count = 0;
        try (Stream<Path> files = Files.list(sessionsDir)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList()) {
                String entryName = "ai/books/" + bookId + "/sessions/" + file.getFileName();
                addFile(zip, file, entryName);
                count++;
            }
        }
        return count;
    }

    private int importSessions(Path tempDir, String packageBookId, String targetBookId) throws IOException {
        Path sourceDir = tempDir.resolve("ai").resolve("books").resolve(packageBookId).resolve("sessions").normalize();
        if (!Files.isDirectory(sourceDir)) return 0;

        Path targetDir = aiBooksDir.resolve(targetBookId).resolve("sessions");
        Files.createDirectories(targetDir);
        int count = 0;
        try (Stream<Path> files = Files.list(sourceDir)) {
            for (Path source : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                JsonParser.parseString(Files.readString(source, StandardCharsets.UTF_8));
                Files.copy(source, targetDir.resolve(source.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                count++;
            }
        }
        return count;
    }

    private void validatePackagePayload(Path tempDir, List<Path> bookFiles) throws IOException {
        for (Path bookFile : bookFiles) {
            JsonParser.parseString(Files.readString(bookFile, StandardCharsets.UTF_8));
            if (bookStore.load(bookFile) == null) {
                throw new IOException("Package contains an unreadable book: " + bookFile.getFileName());
            }

            Path sourceDir = tempDir.resolve("ai").resolve("books").resolve(fileId(bookFile)).resolve("sessions").normalize();
            if (!Files.isDirectory(sourceDir)) continue;
            try (Stream<Path> files = Files.list(sourceDir)) {
                for (Path session : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                    JsonParser.parseString(Files.readString(session, StandardCharsets.UTF_8));
                }
            }
        }
    }

    private void extract(Path source, Path tempDir) throws IOException {
        Path root = tempDir.toAbsolutePath().normalize();
        int entryCount = 0;
        long totalBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(source), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new IOException("Package contains too many files.");
                }

                String name = normalizeEntryName(entry.getName());
                if (!isAllowedEntry(name)) {
                    throw new IOException("Unsupported package entry: " + entry.getName());
                }

                Path target = root.resolve(name).normalize();
                if (!target.startsWith(root)) {
                    throw new IOException("Blocked unsafe package entry: " + entry.getName());
                }

                Files.createDirectories(target.getParent());
                long copied = copyWithLimits(zip, Files.newOutputStream(target));
                totalBytes += copied;
                if (totalBytes > MAX_PACKAGE_BYTES) {
                    throw new IOException("Package is too large.");
                }
            }
        }
    }

    private long copyWithLimits(InputStream input, OutputStream output) throws IOException {
        try (output) {
            byte[] buffer = new byte[8192];
            long copied = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                copied += read;
                if (copied > MAX_ENTRY_BYTES) {
                    throw new IOException("Package entry is too large.");
                }
                output.write(buffer, 0, read);
            }
            return copied;
        }
    }

    private ManifestData readManifest(Path tempDir) throws IOException {
        Path manifestFile = tempDir.resolve("manifest.json");
        if (!Files.isRegularFile(manifestFile)) return new ManifestData();

        ManifestData manifest = gson.fromJson(Files.readString(manifestFile, StandardCharsets.UTF_8), ManifestData.class);
        if (manifest == null || manifest.format == null || manifest.format.isBlank()) return new ManifestData();
        if (!FORMAT.equals(manifest.format) || manifest.formatVersion > FORMAT_VERSION) {
            throw new IOException("Unsupported package format.");
        }
        if (manifest.books == null) manifest.books = new ArrayList<>();
        return manifest;
    }

    private List<Path> orderedBookFiles(Path tempDir, ManifestData manifest) throws IOException {
        Path packageBooksDir = tempDir.resolve("books");
        if (!Files.isDirectory(packageBooksDir)) return List.of();

        Map<String, Path> byId = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(packageBooksDir)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> byId.put(fileId(path), path));
        }

        List<Path> ordered = new ArrayList<>();
        Set<String> used = new HashSet<>();
        if (manifest.books != null) {
            for (ManifestBookData book : manifest.books) {
                if (book == null || book.id == null) continue;
                Path file = byId.get(book.id);
                if (file != null && used.add(book.id)) {
                    ordered.add(file);
                }
            }
        }
        for (Map.Entry<String, Path> entry : byId.entrySet()) {
            if (used.add(entry.getKey())) {
                ordered.add(entry.getValue());
            }
        }
        return ordered;
    }

    private Path resolvePackageCover(Path tempDir, String coverPath) {
        String normalized = safe(coverPath).replace('\\', '/');
        if (!COVER_ENTRY.matcher(normalized).matches()) return null;
        Path root = tempDir.toAbsolutePath().normalize();
        Path cover = root.resolve(normalized).normalize();
        if (!cover.startsWith(root) || !Files.isRegularFile(cover)) return null;
        return cover;
    }

    private void addFile(ZipOutputStream zip, Path source, String entryName) throws IOException {
        if (!Files.isRegularFile(source)) return;
        String normalized = normalizeEntryName(entryName);
        if (!isAllowedEntry(normalized)) return;
        ZipEntry entry = new ZipEntry(normalized);
        zip.putNextEntry(entry);
        Files.copy(source, zip);
        zip.closeEntry();
    }

    private void addText(ZipOutputStream zip, String entryName, String text) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zip.putNextEntry(entry);
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String normalizeEntryName(String entryName) throws IOException {
        if (entryName == null || entryName.isBlank() || entryName.contains("\\") || entryName.startsWith("/") || entryName.contains(":")) {
            throw new IOException("Unsafe package entry: " + entryName);
        }
        return entryName;
    }

    private boolean isAllowedEntry(String name) {
        return name.equals("manifest.json")
                || name.equals("settings.json")
                || BOOK_ENTRY.matcher(name).matches()
                || COVER_ENTRY.matcher(name).matches()
                || SESSION_ENTRY.matcher(name).matches();
    }

    private int countSessions(String bookId) {
        Path sessionsDir = aiBooksDir.resolve(bookId).resolve("sessions");
        if (!Files.isDirectory(sessionsDir)) return 0;
        try (Stream<Path> files = Files.list(sessionsDir)) {
            return (int) files.filter(path -> path.getFileName().toString().endsWith(".json")).count();
        } catch (IOException e) {
            return 0;
        }
    }

    private String newUniqueId(Set<String> liveIds) {
        String id;
        do {
            id = UUID.randomUUID().toString();
        } while (!liveIds.add(id));
        return id;
    }

    private static String fileId(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".json") ? name.substring(0, name.length() - ".json".length()) : name;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private interface SessionCounter {
        int count(String bookId);
    }

    private static final class ManifestData {
        String format = FORMAT;
        int formatVersion = FORMAT_VERSION;
        long createdAt = System.currentTimeMillis();
        String app = "AIWBS";
        String scope = "library";
        List<ManifestBookData> books = new ArrayList<>();

        static ManifestData from(AppState state, SessionCounter sessionCounter) {
            ManifestData data = new ManifestData();
            for (Book book : state.getBooks()) {
                if (book.getId() == null || book.getId().isBlank()) continue;
                ManifestBookData item = new ManifestBookData();
                item.id = book.getId();
                item.name = safe(book.getName());
                item.coverPath = safe(book.getCoverPath()).replace('\\', '/');
                item.sessionCount = sessionCounter.count(book.getId());
                data.books.add(item);
            }
            return data;
        }
    }

    private static final class ManifestBookData {
        String id;
        String name;
        String coverPath;
        int sessionCount;
    }

    private static final class PackageSettingsData {
        int schemaVersion = 1;
        LibraryData library = new LibraryData();

        static PackageSettingsData from(AppState state) {
            PackageSettingsData data = new PackageSettingsData();
            for (Book book : state.getBooks()) {
                if (book.getId() != null && !book.getId().isBlank()) {
                    data.library.bookOrder.add(book.getId());
                }
            }
            data.library.selectedBookId = data.library.bookOrder.isEmpty() ? null : data.library.bookOrder.get(0);
            return data;
        }
    }

    private static final class LibraryData {
        List<String> bookOrder = new ArrayList<>();
        String selectedBookId;
    }
}
