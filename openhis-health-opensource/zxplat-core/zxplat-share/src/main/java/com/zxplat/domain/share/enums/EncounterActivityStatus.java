/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 就诊活动状态枚举。
 */
public enum EncounterActivityStatus implements EnumInterface {
    /** 已安排（EncounterActivityStatus，编码 {@code PLANNED}）。 */
    PLANNED(0L, "PLANNED", "已安排", "", 1L),
    /** 活动中（EncounterActivityStatus，编码 {@code ACTIVE}）。 */
    ACTIVE(0L, "ACTIVE", "活动中", "", 2L),
    /** 已保留（EncounterActivityStatus，编码 {@code RESERVED}）。 */
    RESERVED(0L, "RESERVED", "已保留", "", 3L),
    /** 已完成（EncounterActivityStatus，编码 {@code COMPLETED}）。 */
    COMPLETED(0L, "COMPLETED", "已完成", "", 4L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /** 枚举构造：绑定编码系统、持久化 code、展示名与排序。 */
    EncounterActivityStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
