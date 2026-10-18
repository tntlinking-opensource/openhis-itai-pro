/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 编码系统状态
 *
 * <p>FHIR标准：CodeSystem.status</p>
 *
 */
public enum CodeSystemStatus implements EnumInterface {
/** 草稿 编码系统正在开发中，尚未正式使用（CodeSystemStatus，编码 {@code DRAFT}）。 */
    DRAFT(0L, "DRAFT", "草稿 编码系统正在开发中，尚未正式使用", "", 1L),
/** 活跃 编码系统已正式发布，正在使用中（CodeSystemStatus，编码 {@code ACTIVE}）。 */
    ACTIVE(0L, "ACTIVE", "活跃 编码系统已正式发布，正在使用中", "", 2L),
/** 已废弃 编码系统已被新版本替代，不再使用（CodeSystemStatus，编码 {@code RETIRED}）。 */
    RETIRED(0L, "RETIRED", "已废弃 编码系统已被新版本替代，不再使用", "", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    CodeSystemStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
