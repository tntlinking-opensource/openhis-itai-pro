package com.openhis.whale.report.application.support;

import com.openhis.whale.core.application.dto.PagedResult;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Objects;

/**
 * {@link PagedResult} 构建工具（B.10.11：pageNo 1-based，Spring 页码换算集中在此）。
 */
public final class ReportPagedResultBuilder {

    private ReportPagedResultBuilder() {
    }

    /**
     * 用 Spring Data {@link Page} 元数据 + 已映射 content 构建分页响应。
     */
    public static <T> PagedResult<T> fromPage(List<T> content, Page<?> page) {
        if (page == null) {
            return PagedResult.of(List.of(), 0L, 1, 0);
        }
        return PagedResult.of(
                Objects.requireNonNullElse(content, List.of()),
                page.getTotalElements(),
                page.getNumber() + 1,
                page.getSize());
    }

    /**
     * 对已装配全量列表做内存分页（日汇总等场景）。
     */
    public static <T> PagedResult<T> fromFullList(List<T> allRows, int pageNo, int pageSize) {
        List<T> safeRows = Objects.requireNonNullElse(allRows, List.of());
        long totalCount = safeRows.size();
        List<T> pageRows = slicePage(safeRows, pageNo, pageSize);
        return PagedResult.of(pageRows, totalCount, pageNo, pageSize);
    }

    private static <T> List<T> slicePage(List<T> allRows, int pageNo, int pageSize) {
        if (allRows.isEmpty() || pageSize <= 0) {
            return List.of();
        }
        int safePageNo = pageNo;
        if (safePageNo < 1) {
            safePageNo = 1;
        }
        int fromIndex = (safePageNo - 1) * pageSize;
        if (fromIndex >= allRows.size()) {
            return List.of();
        }
        int toIndex = fromIndex + pageSize;
        if (toIndex > allRows.size()) {
            toIndex = allRows.size();
        }
        return allRows.subList(fromIndex, toIndex);
    }
}
