/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 病案主档状态（持久化 {@code status_code} 列）。
 */
public enum CaseRecordStatus implements EnumInterface {
    /**
     * 草稿：医生可编辑首页。
     */
    DRAFT(0L, "draft", "草稿", "", 0L),
    /**
     * 已提交：等待病案室接收。
     */
    SUBMITTED(0L, "submitted", "已提交", "", 1L),
    /**
     * 已退回：医生需修改后重新提交。
     */
    RETURNED(0L, "returned", "已退回", "", 2L),
    /**
     * 已接收：病案室已确认。
     */
    RECEIVED(0L, "received", "已接收", "", 3L),
/** 质控中（CaseRecordStatus，持久化编码 {@code qcing}）。 */
    QCING(0L, "qcing", "质控中", "", 4L),
    /**
     * 已归档：首页冻结不可编辑。
     */
    ARCHIVED(0L, "archived", "已归档", "", 5L);

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
    CaseRecordStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
