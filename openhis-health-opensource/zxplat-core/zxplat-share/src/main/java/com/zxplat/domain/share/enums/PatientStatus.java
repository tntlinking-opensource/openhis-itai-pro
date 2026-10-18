/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 患者生命周期状态枚举。
 */
public enum PatientStatus implements EnumInterface {
/** 临时建档（PatientStatus，编码 {@code DRAFT}）。 */
    DRAFT(0L, "DRAFT", "临时建档", "", 1L),
/** 正式活跃（PatientStatus，编码 {@code ACTIVE}）。 */
    ACTIVE(0L, "ACTIVE", "正式活跃", "", 2L),
/** 已停用（PatientStatus，编码 {@code INACTIVE}）。 */
    INACTIVE(0L, "INACTIVE", "已停用", "", 3L),
/** 已合并（PatientStatus，编码 {@code MERGED}）。 */
    MERGED(0L, "MERGED", "已合并", "", 4L),
/** 已废弃（PatientStatus，编码 {@code CANCELLED}）。 */
    CANCELLED(0L, "CANCELLED", "已废弃", "", 5L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    PatientStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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

    /**
     * 判断是否为终止状态。
     *
     * @return 终止状态返回 true
     */
    public boolean isTerminal() {
        if (MERGED.equals(this)) {
            return true;
        }
        if (CANCELLED.equals(this)) {
            return true;
        }
        return false;
    }

}
