/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 活动类型枚举（迁移自旧模型 ActivityType）。
 */
public enum ActivityType implements EnumInterface {
    /** 检验（ActivityType，编码 {@code 1}）。 */
    PROOF(0L, "1", "检验", "", 1L),
    /** 检查（ActivityType，编码 {@code 2}）。 */
    TEST(0L, "2", "检查", "", 2L),
    /** 护理（ActivityType，编码 {@code 3}）。 */
    CARE(0L, "3", "护理", "", 3L),
    /** 手术（ActivityType，编码 {@code 4}）。 */
    OPERATION(0L, "4", "手术", "", 4L),
    /** 其他（ActivityType，编码 {@code 5}）。 */
    OTHER(0L, "5", "其他", "", 5L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /** 枚举构造：绑定编码系统、持久化 code、展示名与排序。 */
    ActivityType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    public Long getCodeSystemId() {
        return codeSystemId;
    }

    public String getCode() {
        return code;
    }

    public String getDisplay() {
        return display;
    }

    public String getDefinition() {
        return definition;
    }

    public Long getSortOrder() {
        return sortOrder;
    }
}
