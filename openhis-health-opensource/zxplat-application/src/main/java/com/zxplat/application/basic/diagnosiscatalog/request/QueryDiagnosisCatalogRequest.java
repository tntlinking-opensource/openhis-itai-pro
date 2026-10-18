/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.diagnosiscatalog.request;

import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 疾病目录分页查询请求：支持来源分类与关键字（走领域 {@code DiseasePageQuery}），默认按疾病编码倒序。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryDiagnosisCatalogRequest extends AbstractHealthPageRequest {

    /** 疾病来源/分类业务码；与初始化「疾病分类」下拉的 code 对齐。 */
    private String sourceEnum;

    /** 发布状态 code（{@code ACTIVE}/{@code INACTIVE}）；空表示不限。 */
    private String statusEnum;

    {
        // 限定可参与 where 的属性名，避免客户端传入未白名单字段
        List<String> where = new ArrayList<>();
        where.add("name");
        where.add("conditionCode");
        where.add("pyStr");
        where.add("wbStr");
        this.setWhere(where);
        this.setSortBy(FieldNameConstants.CONDITION_CODE);
    }
}
