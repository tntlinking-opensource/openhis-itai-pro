/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.devicecatalog.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 耗材目录分页查询请求。分页与关键字见 {@link AbstractPageQuery}（B.10.11）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PageDeviceCatalogRequest extends AbstractHealthPageRequest {

    /** 分类过滤。 */
    private String categoryCode;
    /** 耗材种类（{@code device_type_code}）过滤。 */
    private String typeCode;
}
