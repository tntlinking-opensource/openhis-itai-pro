/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 标本合管配置共享常量。
 */
public interface SpecimenTubeMergeConstants {

    /**
     * 采血费诊疗项目名称（产品内置；{@code wkf_activity_definition.name} 精确匹配）。
     *
     * <p>与代煎 {@link DoctorStationTcmConstants#DECOCTION_ACTIVITY_NAME} 同构：按名称解析唯一 activity。
     * 见 {@link BuiltInActivityDefinitionConstants}：禁止删/改名/改类型与类目。</p>
     */
    String BLOOD_COLLECTION_FEE_ACTIVITY_NAME = "采血费";
    /** 检验类诊疗目录类别 code（activity_category_code）。 */
    String LAB_ACTIVITY_CATEGORY_CODE = "22";
}
