/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 门诊挂号/挂号单就诊状态（读模型 {@code status_enum} 持久化码，与历史整数码字符串一致）。
 */
public enum ClinicRegisterStatus implements EnumInterface {

    /** 已挂号（初始状态）。 */
    REGISTERED(0L, "10", "已挂号", "", 10L),

/** 已分诊（ClinicRegisterStatus，持久化编码 {@code 20}）。 */
    TRIAGED(0L, "20", "已分诊", "", 20L),

    /** 就诊中（接诊医生已接诊）。 */
    IN_PROGRESS(0L, "30", "就诊中", "", 30L),

    /** 暂离（就诊中暂时离开）。 */
    ON_HOLD(0L, "40", "暂离", "", 40L),

/** 已完诊（ClinicRegisterStatus，持久化编码 {@code 50}）。 */
    COMPLETED(0L, "50", "已完诊", "", 50L),

/** 已取消（ClinicRegisterStatus，持久化编码 {@code 90}）。 */
    CANCELLED(0L, "90", "已取消", "", 90L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    ClinicRegisterStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
