/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.pagination;

import com.openhis.health.domain.share.query.AbstractPageQuery;

/**
 * {@code health-application} 对外 REST 分页请求体基类（B.10.11）。
 *
 * <p>领域与读模型查询入参仍以 {@link AbstractPageQuery} 为语义真源（含
 * {@link AbstractPageQuery#springPageIndex()} 等）；凡暴露给前端的列表请求 DTO，应继承本类型而非直接继承
 * {@link AbstractPageQuery}。</p>
 *
 * <p>分页窗口绑定见 {@link PageQueryBinder}；出站组装见 {@link PagedResultBuilder}。</p>
 */
public abstract class AbstractHealthPageRequest extends AbstractPageQuery {

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;
}
