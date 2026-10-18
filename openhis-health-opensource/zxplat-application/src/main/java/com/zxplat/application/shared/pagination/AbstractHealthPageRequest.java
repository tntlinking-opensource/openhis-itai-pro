/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.pagination;

/**
 * 兼容别名：分页窗口字段真身在 {@link com.zxplat.framework.core.application.paging.AbstractPageQuery}。
 *
 * <p>直接继承 {@code AbstractPageQuery}，与 framework {@code AbstractHealthPageRequest} 同构，
 * 避免「application 别名 → framework 空壳 → AbstractPageQuery」两级空壳在增量编译时被成片删除导致启动
 * {@code ClassNotFoundException: AbstractHealthPageRequest}。</p>
 *
 * @deprecated 新代码请改 import {@code com.zxplat.framework.core.application.paging.AbstractHealthPageRequest}
 */
@Deprecated
public abstract class AbstractHealthPageRequest
        extends com.zxplat.framework.core.application.paging.AbstractPageQuery {

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;
}
