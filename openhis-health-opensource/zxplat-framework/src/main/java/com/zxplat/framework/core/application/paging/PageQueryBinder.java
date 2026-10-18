/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application.paging;

/**
 * 入站分页工具（B.10.11）：将 HTTP 分页窗口绑定到 {@link AbstractPageQuery}，与 {@link PagedResultBuilder} 对称
 * （{@code PageQueryBinder}：请求 → 查询；{@link PagedResultBuilder}：{@code Page} → 响应）。
 *
 * <p>对外 REST 分页请求体应继承 {@link AbstractHealthPageRequest}；{@link #bind} / {@link #copyToPlainQuery}
 * 仅做字段搬运，不包含门控或业务谓词。</p>
 */
public final class PageQueryBinder {

    /**
     * 禁止实例化：仅提供静态绑定方法。
     */
    private PageQueryBinder() {
        // 工具类禁止实例化
    }

    /**
     * 将 {@code source} 的分页窗口（页码、页大小、关键字、时段、排序、where）写入已存在的 {@code target}。
     *
     * @param source 可为 {@code null}（不修改 target）
     * @param target 可为 {@code null}（直接返回）
     */
    public static void bind(AbstractPageQuery source, AbstractPageQuery target) {
        if (source == null || target == null) {
            return;
        }
        target.setPageNo(source.getPageNo());
        target.setPageSize(source.getPageSize());
        target.setKeyword(source.getKeyword());
        target.setStartDate(source.getStartDate());
        target.setEndDate(source.getEndDate());
        target.setSortBy(source.getSortBy());
        target.setSortDirection(source.getSortDirection());
        target.setWhere(source.getWhere());
    }

    /**
     * 复印分页窗口到新的「纯」{@link AbstractPageQuery} 实例，供只接受 plain 类型的读模型入口使用。
     *
     * @param source 可为 {@code null}（返回空壳实例）
     * @return 新实例；字段与 {@code source} 一致（含 {@code null} 的显式拷贝）
     */
    public static AbstractPageQuery copyToPlainQuery(AbstractPageQuery source) {
        AbstractPageQuery target = new AbstractPageQuery();
        bind(source, target);
        return target;
    }

    /**
     * 嵌套别名，兼容既有 {@code import PageQueryBinder.AbstractHealthPageRequest}。
     *
     * <p>新代码请直接继承顶层 {@link AbstractHealthPageRequest}。</p>
     */
    public static abstract class AbstractHealthPageRequest
            extends com.zxplat.framework.core.application.paging.AbstractHealthPageRequest {

        /** 序列化版本号。 */
        private static final long serialVersionUID = 1L;
    }
}
