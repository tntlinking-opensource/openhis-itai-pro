/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.labdevice.request;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 检验设备分页查询请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PageLabDeviceRequest extends AbstractHealthPageRequest {

    /** 发布状态过滤。 */
    private String statusEnum;

    /** 设备分类过滤。 */
    private String categoryCode;

    /** 归属科室过滤。 */
    private String orgId;

    {
        List<String> where = new ArrayList<>();
        where.add(FieldNameConstants.NAME);
        where.add(FieldNameConstants.BUS_NO);
        where.add(FieldNameConstants.PY_STR);
        where.add(FieldNameConstants.WB_STR);
        this.setWhere(where);
        this.setSortBy(FieldNameConstants.UPDATED_AT);
    }
}
