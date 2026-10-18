/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application.paging;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * 应用层分页响应基类（B.10.11）。
 *
 * <p>与 {@link com.zxplat.framework.core.application.dto.PagedResult} 同构，供 Response DTO 继承，
 * 与入站 {@link AbstractHealthPageRequest} / {@link PagedResultBuilder} 同包。</p>
 *
 * <p>本类保留静态工厂，避免「仅 extends 的空壳」在增量编译删类窗口期被解析成无包名 {@code PagedResult}。</p>
 *
 * @param <T> 分页行类型
 */
public class PagedResult<T> extends com.zxplat.framework.core.application.dto.PagedResult<T> {

    /**
     * @param items      当前页
     * @param totalCount 总条数
     * @param pageNo     1-based 页码
     * @param pageSize   页大小
     * @param <T>        行类型
     * @return 分页结果
     */
    public static <T> PagedResult<T> of(List<T> items, long totalCount, int pageNo, int pageSize) {
        PagedResult<T> result = new PagedResult<>();
        result.setContent(items);
        result.setTotalCount(totalCount);
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        return result;
    }

    /**
     * @param page Spring Data 分页（0-based number → 1-based pageNo）
     * @param <T>  行类型
     * @return 分页结果
     */
    public static <T> PagedResult<T> fromPage(Page<T> page) {
        PagedResult<T> result = new PagedResult<>();
        result.setContent(page.getContent());
        result.setTotalCount(page.getTotalElements());
        result.setPageNo(page.getNumber() + 1);
        result.setPageSize(page.getSize());
        return result;
    }
}
