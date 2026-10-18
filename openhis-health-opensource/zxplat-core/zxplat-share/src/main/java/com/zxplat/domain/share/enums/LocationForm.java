/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

import lombok.Getter;

/**
 * 位置物理形式枚举。
 */
@Getter
public enum LocationForm implements EnumInterface {
    /** 病区（LocationForm，编码 {@code WARD}）。 */
    WARD(0L, "WARD", "病区", "", 4L),
    /** 床位（LocationForm，编码 {@code BED}）。 */
    BED(0L, "BED", "床位", "", 8L),
    /** 病房（LocationForm，编码 {@code HOUSE}）。 */
    HOUSE(0L, "HOUSE", "病房", "", 10L),
    /** 药房（LocationForm，编码 {@code PHARMACY}）。 */
    PHARMACY(0L, "PHARMACY", "药房", "", 16L),
    /** 仓库（LocationForm，编码 {@code WAREHOUSE}）。 */
    WAREHOUSE(0L, "WAREHOUSE", "仓库", "", 17L),
    /** 科室药房（LocationForm，编码 {@code DEPARTMENT_PHARMACY}）。 */
    DEPARTMENT_PHARMACY(0L, "DEPARTMENT_PHARMACY", "科室药房", "", 18L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    LocationForm(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

}
