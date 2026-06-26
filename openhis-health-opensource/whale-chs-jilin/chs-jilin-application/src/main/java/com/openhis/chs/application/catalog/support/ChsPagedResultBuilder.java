package com.openhis.chs.application.catalog.support;

import com.openhis.whale.core.application.dto.PagedResult;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Objects;

/** 分页响应构建。 */
public final class ChsPagedResultBuilder {

    private ChsPagedResultBuilder() {
    }

    public static <T, R extends PagedResult<T>> R build(R response, List<T> content, Page<?> page) {
        if (page == null) {
            response.setContent(List.of());
            response.setTotalCount(0L);
            response.setPageNo(1);
            response.setPageSize(0);
            return response;
        }
        response.setContent(Objects.requireNonNullElse(content, List.of()));
        response.setTotalCount(page.getTotalElements());
        response.setPageNo(page.getNumber() + 1);
        response.setPageSize(page.getSize());
        return response;
    }
}
