/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.share;

import com.zxplat.framework.core.application.dto.PagedResult;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Objects;

/**
 * {@link PagedResult} 构建工具（pageNo 1-based）。
 */
public final class CampusPagedResultBuilder {

    private CampusPagedResultBuilder() {
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
