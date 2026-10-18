/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

import java.util.Map;
import java.util.Set;

import com.zxplat.framework.core.application.util.StringUtils;

/**
 * 产品内置诊疗项目（按 {@code name} 精确匹配；Flyway V42 种子）。
 *
 * <p>代码多处按名称解析，缺失则皮试/嘱托/代煎/合管采血费等链路失败。
 * 写侧禁止删除、改名、改类型（{@code type_enum}）与改类目（{@code category_code}）。</p>
 *
 * <p>名称真源仍落在各业务常量；本接口仅汇总「内置集合」与锁定类目/类型。</p>
 */
public interface BuiltInActivityDefinitionConstants {

    /**
     * 内置名称集合（trim 后精确匹配）。
     */
    Set<String> BUILT_IN_NAMES = Set.of(
        GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME,
        GeneralSharedConstants.ENTRUST_ACTIVITY_NAME,
        DoctorStationTcmConstants.DECOCTION_ACTIVITY_NAME,
        SpecimenTubeMergeConstants.BLOOD_COLLECTION_FEE_ACTIVITY_NAME);

    /**
     * 内置项锁定类目：name → {@code category_code}。
     *
     * <p>「其他」类目码 31 与术语 activity_category_code 一致（代煎中药）。</p>
     */
    Map<String, String> LOCKED_CATEGORY_CODE_BY_NAME = Map.of(
        GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME,
        ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROOF,
        GeneralSharedConstants.ENTRUST_ACTIVITY_NAME,
        ServiceRequestCategoryConstants.SERVICE_CATEGORY_ENTRUST,
        DoctorStationTcmConstants.DECOCTION_ACTIVITY_NAME,
        "31",
        SpecimenTubeMergeConstants.BLOOD_COLLECTION_FEE_ACTIVITY_NAME,
        ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROOF);

    /**
     * 内置项锁定的目录标签 {@code type_enum}（术语 activity_type_code 种子码；非业务通道）。
     */
    Map<String, String> LOCKED_TYPE_ENUM_BY_NAME = Map.of(
        GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME,
        "1",
        GeneralSharedConstants.ENTRUST_ACTIVITY_NAME,
        "5",
        DoctorStationTcmConstants.DECOCTION_ACTIVITY_NAME,
        "5",
        SpecimenTubeMergeConstants.BLOOD_COLLECTION_FEE_ACTIVITY_NAME,
        "1");

    /**
     * 是否为产品内置诊疗项目名称。
     *
     * @param name 项目名称（可含首尾空白）
     * @return true 表示内置
     */
    static boolean isBuiltInName(String name) {
        if (StringUtils.isEmpty(name)) {
            return false;
        }
        return BUILT_IN_NAMES.contains(name.trim());
    }
}
