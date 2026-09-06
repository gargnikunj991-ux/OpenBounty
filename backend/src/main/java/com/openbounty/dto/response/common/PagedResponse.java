package com.openbounty.dto.response.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Generic standardized envelope for paginated API responses.
 *
 * @param <T> Element type contained in the page content
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Standard paginated response envelope")
public class PagedResponse<T> {

    @Schema(description = "List of items in current page")
    private List<T> content;

    @Schema(description = "Zero-based current page index", example = "0")
    private int pageNumber;

    @Schema(description = "Configured page size", example = "10")
    private int pageSize;

    @Schema(description = "Total number of matching elements across all pages", example = "100")
    private long totalElements;

    @Schema(description = "Total number of available pages", example = "10")
    private int totalPages;

    @Schema(description = "Indicates if current page is the last page", example = "false")
    private boolean last;

    @Schema(description = "Indicates if current page is the first page", example = "true")
    private boolean first;

    @Schema(description = "Indicates if the page contains zero elements", example = "false")
    private boolean empty;

    public static <T> PagedResponse<T> from(Page<T> page) {
        return PagedResponse.<T>builder()
                .content(page.getContent())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .first(page.isFirst())
                .empty(page.isEmpty())
                .build();
    }

    public static <E, T> PagedResponse<T> from(Page<E> page, Function<E, T> mapper) {
        List<T> content = page.getContent().stream().map(mapper).toList();
        return PagedResponse.<T>builder()
                .content(content)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .first(page.isFirst())
                .empty(page.isEmpty())
                .build();
    }
}
