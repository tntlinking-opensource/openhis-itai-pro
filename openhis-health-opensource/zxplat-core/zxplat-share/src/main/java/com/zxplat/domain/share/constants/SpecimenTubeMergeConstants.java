/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

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

    /**
     * 设备内合管组默认序号（存量迁移与首组）。
     *
     * <p>展示名由应用层拼「组」+ 序号，不落库组名。</p>
     */
    int DEFAULT_GROUP_NO = 1;

    /** 设备内合管组序号下限（含）。 */
    int MIN_GROUP_NO = 1;

    /**
     * 合管配置页检索检验类诊疗项目时的默认条数上限（IF-STM-05）。
     *
     * <p>防止下拉一次拉全库；请求显式传更大 {@code limit} 时以请求为准。</p>
     */
    int DEFAULT_ACTIVITY_SEARCH_LIMIT = 50;
}
