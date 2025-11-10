package sh.harold.sprite.core;

import java.util.List;
import java.util.Objects;

public final class Pagination {
    private Pagination() {
    }

    public static <T> Page<T> slice(List<T> source, int requestedPage, int pageSize) {
        Objects.requireNonNull(source, "source");
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be > 0");
        }

        int totalItems = source.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) pageSize));
        int page = Math.max(1, Math.min(requestedPage, totalPages));
        int fromIndex = Math.min((page - 1) * pageSize, totalItems);
        int toIndex = Math.min(fromIndex + pageSize, totalItems);

        List<T> slice = source.subList(fromIndex, toIndex);
        return new Page<>(List.copyOf(slice), page, totalPages, totalItems);
    }

    public record Page<T>(List<T> items, int page, int totalPages, int totalItems) {
        public boolean hasPrevious() {
            return page > 1;
        }

        public boolean hasNext() {
            return page < totalPages;
        }
    }
}
