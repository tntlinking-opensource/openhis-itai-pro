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
     * 采血费诊疗项目名称（{@code wkf_activity_definition.name} 精确匹配）。
     *
     * <p>与代煎 {@link com.openhis.health.domain.share.constants.DoctorStationTcmConstants#DECOCTION_ACTIVITY_NAME}
     * 同构：前端勾选展示名写死本常量；保存时按名称解析唯一 activity 并写入合管表。</p>
     */
    String BLOOD_COLLECTION_FEE_ACTIVITY_NAME = "采血费";

    /** 检验类诊疗目录类别 code（activity_category_code）。 */
    String LAB_ACTIVITY_CATEGORY_CODE = "22";
}
