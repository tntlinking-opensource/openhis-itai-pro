/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.pagination;

/**
 * 健康域分页响应基类（B.10.11）。
 *
 * <p>与 {@link com.openhis.whale.core.application.dto.PagedResult} 同构，供 application 层 Response DTO 继承，
 * 避免各模块直接依赖 whale-core DTO 包路径不一致。</p>
 *
 * @param <T> 分页行类型
 */
public class PagedResult<T> extends com.openhis.whale.core.application.dto.PagedResult<T> {
}
