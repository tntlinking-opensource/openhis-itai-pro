/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.code.request;

import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 查询编码系统请求（编码系统管理菜单）
 *
 * <p>分页见 {@link AbstractPageQuery}；综合关键字字段为 {@code keyword}。</p>
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryCodeSystemRequest extends AbstractHealthPageRequest {

    /** 按体系名称过滤。 */
    private String name;
    /** 按状态过滤（字符串码）。 */
    private String status;
}
