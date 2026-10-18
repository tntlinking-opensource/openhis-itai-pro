/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application.dto;

import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 分页响应格式（对应 ABP 的 PagedResultDto）。
 *
 * <p>字段 {@link #pageNo} 表示<strong>从 1 开始</strong>的当前页码，与业务侧 {@code AbstractPageQuery#pageNo} 请求语义一致；
 * Spring Data {@link Page#getNumber()} 为 0-based，在 {@link #fromPage(Page)} 中转换为 1-based 写入本 DTO。</p>
 */
@Data
public class PagedResult<T> {

    private List<T> content;
    private long totalCount;
    /** 当前页码（从 1 开始）。 */
    private int pageNo;
    private int pageSize;

    public static <T> PagedResult<T> fromPage(Page<T> page) {
        PagedResult<T> result = new PagedResult<>();
        result.setContent(page.getContent());
        result.setTotalCount(page.getTotalElements());
        result.setPageNo(page.getNumber() + 1);
        result.setPageSize(page.getSize());
        return result;
    }

    /**
     * @param pageNo   当前页码（从 1 开始）
     * @param pageSize 每页条数
     */
    public static <T> PagedResult<T> of(List<T> items, long totalCount, int pageNo, int pageSize) {
        PagedResult<T> result = new PagedResult<>();
        result.setContent(items);
        result.setTotalCount(totalCount);
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        return result;
    }
}
