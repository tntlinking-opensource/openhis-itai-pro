/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.specimendefinition.request;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 样本定义分页查询请求：支持关键字、发布状态与标本种类筛选。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PageSpecimenDefinitionRequest extends AbstractHealthPageRequest {

    /** 发布状态过滤，存 {@code PublicationStatus.getCode()}。 */
    private String statusEnum;

    /** 标本种类过滤，存 {@code SpecimenType.getCode()}。 */
    private String typeEnum;

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
