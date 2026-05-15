package example.aiwbs.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import example.aiwbs.model.Book;
import example.aiwbs.model.Chapter;
import example.aiwbs.model.ChapterVersion;
import example.aiwbs.model.OutlineNode;
import example.aiwbs.model.Volume;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class BookStore {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String COVERS_DIR_NAME = "covers";

    private final Path dataDir;
    private final Path booksDir;
    private final Path coversDir;
    private final Gson gson;
    private Set<String> loadedBookFileIds = new HashSet<>();

    BookStore(Path dataDir) {
        this.dataDir = dataDir;
        this.booksDir = dataDir.resolve("books");
        this.coversDir = dataDir.resolve(COVERS_DIR_NAME);
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .serializeNulls()
                .create();
    }

    List<Book> loadAll(List<String> bookOrder) {
        if (!Files.isDirectory(booksDir)) {
            return new ArrayList<>();
        }

        Map<String, Book> byId = new LinkedHashMap<>();
        Set<String> fileIds = new HashSet<>();
        try (var files = Files.list(booksDir)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> {
                        Book book = load(path);
                        if (book != null && book.getId() != null && !book.getId().isBlank()) {
                            fileIds.add(fileId(path));
                            byId.putIfAbsent(book.getId(), book);
                        }
                    });
        } catch (IOException ignored) {
        }
        loadedBookFileIds = fileIds;

        List<Book> ordered = new ArrayList<>();
        Set<String> used = new HashSet<>();
        if (bookOrder != null) {
            for (String id : bookOrder) {
                Book book = byId.get(id);
                if (book != null && used.add(id)) {
                    ordered.add(book);
                }
            }
        }
        for (Map.Entry<String, Book> entry : byId.entrySet()) {
            if (used.add(entry.getKey())) {
                ordered.add(entry.getValue());
            }
        }
        return ordered;
    }

    void saveAll(List<Book> books) throws IOException {
        Files.createDirectories(booksDir);
        Set<String> liveIds = new HashSet<>();
        Set<String> liveCoverFiles = new HashSet<>();
        for (Book book : books) {
            ensureBookId(book);
            migrateCoverToProjectFolder(book);
            liveIds.add(book.getId());
            String coverFile = coverFileName(book.getCoverPath());
            if (coverFile != null) {
                liveCoverFiles.add(coverFile);
            }
            BookData data = BookData.from(book);
            writeJson(booksDir.resolve(book.getId() + ".json"), gson.toJson(data));
        }
        deleteStaleBookFiles(liveIds);
        deleteStaleCoverFiles(liveCoverFiles);
        loadedBookFileIds = new HashSet<>(liveIds);
    }

    Book load(Path file) {
        try {
            BookData data = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), BookData.class);
            return data == null ? null : data.toBook();
        } catch (Exception e) {
            return null;
        }
    }

    String importCover(Book book, Path source) throws IOException {
        ensureBookId(book);
        if (source == null || !Files.isRegularFile(source)) return safe(book.getCoverPath());
        Files.createDirectories(coversDir);
        String extension = coverExtension(source);
        Path target = coversDir.resolve(book.getId() + extension);
        Path normalizedSource = source.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedSource.equals(normalizedTarget)) {
            Files.copy(normalizedSource, normalizedTarget, StandardCopyOption.REPLACE_EXISTING);
        }
        return toProjectRelativePath(normalizedTarget);
    }

    Path resolveProjectFile(String path) {
        if (path == null || path.isBlank()) return null;
        Path raw = Path.of(path);
        if (raw.isAbsolute()) return raw.normalize();
        return dataDir.resolve(raw).normalize();
    }

    private void deleteStaleBookFiles(Set<String> liveIds) {
        try (var files = Files.list(booksDir)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> {
                        String id = fileId(path);
                        return loadedBookFileIds.contains(id) && !liveIds.contains(id);
                    })
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private void deleteStaleCoverFiles(Set<String> liveCoverFiles) {
        if (!Files.isDirectory(coversDir)) return;
        try (var files = Files.list(coversDir)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> !liveCoverFiles.contains(path.getFileName().toString()))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private static String fileId(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".json") ? name.substring(0, name.length() - ".json".length()) : name;
    }

    private static void writeJson(Path target, String json) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void ensureBookId(Book book) {
        if (book.getId() == null || book.getId().isBlank()) {
            book.setId(UUID.randomUUID().toString());
        }
    }

    private void migrateCoverToProjectFolder(Book book) {
        String coverPath = safe(book.getCoverPath());
        if (coverPath.isBlank()) return;
        Path resolved = resolveProjectFile(coverPath);
        if (resolved == null || !Files.isRegularFile(resolved)) return;
        if (isProjectRelativePath(coverPath)) return;
        try {
            book.setCoverPath(importCover(book, resolved));
        } catch (IOException ignored) {
        }
    }

    private boolean isProjectRelativePath(String path) {
        if (path == null || path.isBlank()) return false;
        Path raw = Path.of(path);
        if (raw.isAbsolute()) return false;
        Path normalized = raw.normalize();
        return normalized.getNameCount() > 0 && normalized.getName(0).toString().equals(COVERS_DIR_NAME);
    }

    private String coverFileName(String path) {
        if (!isProjectRelativePath(path)) return null;
        return Path.of(path).normalize().getFileName().toString();
    }

    private String toProjectRelativePath(Path absolutePath) {
        Path relative = dataDir.toAbsolutePath().normalize().relativize(absolutePath.toAbsolutePath().normalize());
        return relative.toString().replace('\\', '/');
    }

    private static String coverExtension(Path source) {
        String name = source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot).toLowerCase() : ".png";
        return switch (extension) {
            case ".png", ".jpg", ".jpeg", ".webp" -> extension;
            default -> ".png";
        };
    }

    private static final class BookData {
        int schemaVersion = 1;
        String id;
        String name;
        String coverPath;
        String summary;
        List<OutlineNodeData> outlineRoots = new ArrayList<>();
        List<VolumeData> volumes = new ArrayList<>();

        static BookData from(Book book) {
            BookData data = new BookData();
            data.id = book.getId();
            data.name = safe(book.getName(), "Untitled Book");
            data.coverPath = safe(book.getCoverPath());
            data.summary = safe(book.getSummary());
            data.outlineRoots = OutlineNodeData.fromAll(book.getOutlineRoots());
            data.volumes = VolumeData.fromAll(book.getVolumes());
            return data;
        }

        Book toBook() {
            Book book = new Book(safe(name, "Untitled Book"));
            book.setId(safe(id, UUID.randomUUID().toString()));
            book.setCoverPath(safe(coverPath));
            book.setSummary(safe(summary));
            if (outlineRoots != null) {
                for (OutlineNodeData node : outlineRoots) {
                    book.getOutlineRoots().add(node.toOutlineNode());
                }
            }
            if (volumes != null) {
                for (VolumeData volume : volumes) {
                    book.getVolumes().add(volume.toVolume());
                }
            }
            return book;
        }
    }

    private static final class OutlineNodeData {
        String id;
        String title;
        String content;
        int sortOrder;
        List<OutlineNodeData> children = new ArrayList<>();

        static List<OutlineNodeData> fromAll(List<OutlineNode> nodes) {
            List<OutlineNodeData> data = new ArrayList<>();
            for (OutlineNode node : nodes) {
                data.add(from(node));
            }
            return data;
        }

        static OutlineNodeData from(OutlineNode node) {
            OutlineNodeData data = new OutlineNodeData();
            data.id = node.getId();
            data.title = safe(node.getTitle(), "Untitled Outline");
            data.content = safe(node.getContent());
            data.sortOrder = node.getSortOrder();
            data.children = fromAll(node.getChildren());
            return data;
        }

        OutlineNode toOutlineNode() {
            OutlineNode node = new OutlineNode(safe(id, UUID.randomUUID().toString()), safe(title, "Untitled Outline"));
            node.setContent(safe(content));
            node.setSortOrder(sortOrder);
            if (children != null) {
                for (OutlineNodeData child : children) {
                    node.getChildren().add(child.toOutlineNode());
                }
            }
            return node;
        }
    }

    private static final class VolumeData {
        String name;
        String outlineContent;
        List<ChapterData> chapters = new ArrayList<>();

        static List<VolumeData> fromAll(List<Volume> volumes) {
            List<VolumeData> data = new ArrayList<>();
            for (Volume volume : volumes) {
                data.add(from(volume));
            }
            return data;
        }

        static VolumeData from(Volume volume) {
            VolumeData data = new VolumeData();
            data.name = safe(volume.getName(), "Untitled Volume");
            data.outlineContent = safe(volume.getOutlineContent());
            data.chapters = ChapterData.fromAll(volume.getChapters());
            return data;
        }

        Volume toVolume() {
            Volume volume = new Volume(safe(name, "Untitled Volume"));
            volume.setOutlineContent(safe(outlineContent));
            if (chapters != null) {
                for (ChapterData chapter : chapters) {
                    volume.getChapters().add(chapter.toChapter());
                }
            }
            return volume;
        }
    }

    private static final class ChapterData {
        String id;
        String title;
        String content;
        String outlineContent;
        List<ChapterVersionData> versions = new ArrayList<>();

        static List<ChapterData> fromAll(List<Chapter> chapters) {
            List<ChapterData> data = new ArrayList<>();
            for (Chapter chapter : chapters) {
                data.add(from(chapter));
            }
            return data;
        }

        static ChapterData from(Chapter chapter) {
            ChapterData data = new ChapterData();
            data.id = safe(chapter.getId(), UUID.randomUUID().toString());
            data.title = safe(chapter.getTitle(), "Untitled Chapter");
            data.content = safe(chapter.getContent());
            data.outlineContent = safe(chapter.getOutlineContent());
            data.versions = ChapterVersionData.fromAll(chapter.getVersions());
            return data;
        }

        Chapter toChapter() {
            Chapter chapter = new Chapter(safe(title, "Untitled Chapter"), safe(content));
            chapter.setId(safe(id, UUID.randomUUID().toString()));
            chapter.setOutlineContent(safe(outlineContent));
            if (versions != null) {
                for (ChapterVersionData version : versions) {
                    chapter.getVersions().add(version.toChapterVersion());
                }
            }
            return chapter;
        }
    }

    private static final class ChapterVersionData {
        String savedAt;
        String content;
        int wordCount;

        static List<ChapterVersionData> fromAll(List<ChapterVersion> versions) {
            List<ChapterVersionData> data = new ArrayList<>();
            for (ChapterVersion version : versions) {
                data.add(from(version));
            }
            return data;
        }

        static ChapterVersionData from(ChapterVersion version) {
            ChapterVersionData data = new ChapterVersionData();
            data.savedAt = version.getSavedAt() == null ? null : version.getSavedAt().format(DATE_TIME);
            data.content = safe(version.getContent());
            data.wordCount = version.getWordCount();
            return data;
        }

        ChapterVersion toChapterVersion() {
            return new ChapterVersion(parseDateTime(savedAt), safe(content), wordCount);
        }
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value, DATE_TIME);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
