/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

import lombok.Getter;

/**
 * 过敏原类别（{@code cln_allergy_intolerance.category_code}）。
 */
@Getter
public enum AllergyCategory implements EnumInterface {

    /** 药物。 */
    DRUG(0L, "drug", "药物", "药品、造影剂等药物过敏", 1L),
    /** 食物。 */
    FOOD(0L, "food", "食物", "食物过敏", 2L),
    /** 环境。 */
    ENVIRONMENT(0L, "environment", "环境", "花粉、尘螨等环境过敏", 3L),
    /** 其他。 */
    OTHER(0L, "other", "其他", "未归入药物/食物/环境的过敏原", 4L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    AllergyCategory(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }
}
