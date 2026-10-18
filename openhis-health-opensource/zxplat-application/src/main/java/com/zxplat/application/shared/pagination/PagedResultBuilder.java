/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.pagination;

import java.util.List;

import org.springframework.data.domain.Page;

import com.zxplat.framework.core.application.dto.PagedResult;

/**
 * 兼容门面：真身见 {@link com.zxplat.framework.core.application.paging.PagedResultBuilder}。
 *
 * @deprecated 新代码请改 import {@code com.zxplat.framework.core.application.paging.PagedResultBuilder}
 */
@Deprecated
public final class PagedResultBuilder {

    /**
     * 禁止实例化：仅提供静态构建方法。
     */
    private PagedResultBuilder() {
        // 工具类禁止实例化
    }

    /**
     * 构建分页响应（完整参数）。
     *
     * @param response   响应壳
     * @param content    当前页
     * @param totalCount 总条数
     * @param pageNo     1-based 页码
     * @param pageSize   页大小
     * @param <T>        行类型
     * @param <R>        响应类型
     * @return 响应
     */
    public static <T, R extends PagedResult<T>> R build(
            R response,
            List<T> content,
            long totalCount,
            int pageNo,
            int pageSize) {
        return com.zxplat.framework.core.application.paging.PagedResultBuilder.build(
                response, content, totalCount, pageNo, pageSize);
    }

    /**
     * 用 Spring Data {@link Page} 元数据 + 已映射 content 构建响应。
     *
     * @param response 响应壳
     * @param content  当前页业务行
     * @param page     Spring 分页壳
     * @param <T>      行类型
     * @param <R>      响应类型
     * @return 响应
     */
    public static <T, R extends PagedResult<T>> R build(R response, List<T> content, Page<?> page) {
        return com.zxplat.framework.core.application.paging.PagedResultBuilder.build(response, content, page);
    }

    /**
     * 从 Spring Data Page 构建响应。
     *
     * @param response 响应壳
     * @param page     Spring Page
     * @param <T>      行类型
     * @param <R>      响应类型
     * @return 响应
     */
    public static <T, R extends PagedResult<T>> R buildFromPage(R response, Page<T> page) {
        return com.zxplat.framework.core.application.paging.PagedResultBuilder.buildFromPage(response, page);
    }

    /**
     * 构建全量列表响应（不分页）。
     *
     * @param response 响应壳
     * @param content  全量行
     * @param <T>      行类型
     * @param <R>      响应类型
     * @return 响应
     */
    public static <T, R extends PagedResult<T>> R buildList(R response, List<T> content) {
        return com.zxplat.framework.core.application.paging.PagedResultBuilder.buildList(response, content);
    }

    /**
     * 构建空列表响应。
     *
     * @param response 响应壳
     * @param <T>      行类型
     * @param <R>      响应类型
     * @return 空响应
     */
    public static <T, R extends PagedResult<T>> R buildEmpty(R response) {
        return com.zxplat.framework.core.application.paging.PagedResultBuilder.buildEmpty(response);
    }

    /**
     * 对已装配全量列表做内存分页。
     *
     * @param response 响应壳
     * @param allRows  全量行
     * @param pageNo   1-based 页码
     * @param pageSize 页大小
     * @param <T>      行类型
     * @param <R>      响应类型
     * @return 分页响应
     */
    public static <T, R extends PagedResult<T>> R buildFromFullList(
            R response, List<T> allRows, int pageNo, int pageSize) {
        return com.zxplat.framework.core.application.paging.PagedResultBuilder.buildFromFullList(
                response, allRows, pageNo, pageSize);
    }
}
