/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.pagination;

import com.zxplat.domain.share.query.AbstractPageQuery;

/**
 * 兼容门面：真身见 {@link com.zxplat.framework.core.application.paging.PageQueryBinder}。
 *
 * <p>入参使用 framework {@code AbstractPageQuery}，以便 REST 请求（继承 {@link AbstractHealthPageRequest}）
 * 与领域 Query（继承 domain 兼容子类）均可绑定。</p>
 *
 * @deprecated 新代码请改 import {@code com.zxplat.framework.core.application.paging.PageQueryBinder}
 */
@Deprecated
public final class PageQueryBinder {

    /**
     * 禁止实例化：仅提供静态绑定方法。
     */
    private PageQueryBinder() {
        // 工具类禁止实例化
    }

    /**
     * 将 {@code source} 的分页窗口写入 {@code target}（委托 framework）。
     *
     * @param source 源；可为 null
     * @param target 目标；可为 null
     */
    public static void bind(
            com.zxplat.framework.core.application.paging.AbstractPageQuery source,
            com.zxplat.framework.core.application.paging.AbstractPageQuery target) {
        com.zxplat.framework.core.application.paging.PageQueryBinder.bind(source, target);
    }

    /**
     * 复印分页窗口到新的领域 {@link AbstractPageQuery} 实例。
     *
     * @param source 源；可为 null
     * @return 领域侧 plain 查询实例
     */
    public static AbstractPageQuery copyToPlainQuery(
            com.zxplat.framework.core.application.paging.AbstractPageQuery source) {
        AbstractPageQuery target = new AbstractPageQuery();
        bind(source, target);
        return target;
    }

    /**
     * 嵌套别名，兼容既有 {@code import PageQueryBinder.AbstractHealthPageRequest}。
     *
     * @deprecated 请继承 {@link AbstractHealthPageRequest} 或 framework 顶层类型
     */
    @Deprecated
    public static abstract class AbstractHealthPageRequest
            extends com.zxplat.application.shared.pagination.AbstractHealthPageRequest {

        /** 序列化版本号。 */
        private static final long serialVersionUID = 1L;
    }
}
