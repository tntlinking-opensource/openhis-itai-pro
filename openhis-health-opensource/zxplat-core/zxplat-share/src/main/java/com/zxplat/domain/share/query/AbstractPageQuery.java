/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.query;

/**
 * 领域侧分页读查询基类（B.10.11）——兼容子类。
 *
 * <p>字段与行为真身在 {@link com.zxplat.framework.core.application.paging.AbstractPageQuery}；本类仅保留
 * {@code domain.share.query} 包路径，避免既有领域 Query 大面积改 import。</p>
 */
public class AbstractPageQuery extends com.zxplat.framework.core.application.paging.AbstractPageQuery {

    private static final long serialVersionUID = 1L;
}
