/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 医疗服务状态枚举。
 *
 * <p>对应 {@code adm_healthcare_service.status_enum} 的存量码值体系：
 * DRAFT=0、ACTIVE=1、RETIRED=2、DELETED=3。</p>
 */
public enum HealthcareServiceStatus implements EnumInterface {
/** 草稿（信息录入中，尚未对外发布）（HealthcareServiceStatus，编码 {@code DRAFT}）。 */
    DRAFT(0L, "DRAFT", "草稿（信息录入中，尚未对外发布）", "", 0L),
/** 已发布（生效中，可被挂号/预约流程使用）（HealthcareServiceStatus，编码 {@code ACTIVE}）。 */
    ACTIVE(0L, "ACTIVE", "已发布（生效中，可被挂号/预约流程使用）", "", 1L),
/** 已停用（暂时下线，可重新发布）（HealthcareServiceStatus，编码 {@code RETIRED}）。 */
    RETIRED(0L, "RETIRED", "已停用（暂时下线，可重新发布）", "", 2L),
/** 已废弃（逻辑删除，不可恢复）（HealthcareServiceStatus，编码 {@code DELETED}）。 */
    DELETED(0L, "DELETED", "已废弃（逻辑删除，不可恢复）", "", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    HealthcareServiceStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
