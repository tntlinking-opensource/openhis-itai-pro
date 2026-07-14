/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 病案工作流角色（状态校验用）。
 */
public enum WorkflowRole implements EnumInterface {
    /**
     * 住院医生：可编辑草稿/退回态首页并提交。
     */
    DOCTOR(0L, "doctor", "住院医生", "", 0L),
    /**
     * 病案室：可接收/退回。
     */
    RECORD_ROOM(0L, "recordRoom", "病案室", "", 1L),
    /**
     * 系统内部：离院建档等。
     */
    SYSTEM(0L, "system", "系统", "", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举常量构造，绑定持久化 {@code code}、展示名与排序。
     *
     * @param codeSystemId 编码体系主键（预留）
     * @param code 持久化 code
     * @param display 界面展示名
     * @param definition 定义说明
     * @param sortOrder 排序号
     */
    WorkflowRole(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
