package example.aiwbs.storage;

import java.util.List;

public record PackageTransferResult(
        int bookCount,
        int sessionCount,
        List<String> bookIds,
        List<String> bookNames
) {
    public PackageTransferResult {
        bookIds = List.copyOf(bookIds);
        bookNames = List.copyOf(bookNames);
    }
}
