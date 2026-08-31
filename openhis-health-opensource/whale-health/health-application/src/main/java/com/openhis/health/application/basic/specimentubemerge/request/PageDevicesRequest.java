/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.specimentubemerge.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 合管配置左栏：启用检验设备分页检索请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PageDevicesRequest extends AbstractHealthPageRequest {

    /** 设备分类过滤（术语 adm_device_category_code）。 */
    private String categoryCode;

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
