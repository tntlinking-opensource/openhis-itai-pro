/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 病案主档<strong>归档</strong>状态（持久化 {@code status_code}）。
 *
 * <p>仅三态：{@code DRAFT} / {@code PENDING_ARCHIVE} / {@code ARCHIVED}。
 * 质控、收案、退回等历史流转<strong>不</strong>占用本枚举；质控以后另开字段/子域。</p>
 */
public enum CaseRecordStatus implements EnumInterface {
    /**
     * 草稿：医生可编辑首页；可提交归档。
     */
    DRAFT(0L, "DRAFT", "草稿", "", 0L),
    /**
     * 待归档审批：已提交，等待病案管理员同意/驳回。
     */
    PENDING_ARCHIVE(0L, "PENDING_ARCHIVE", "待归档审批", "", 1L),
    /**
     * 已归档：首页只读；修改须经首页编辑申请。
     */
    ARCHIVED(0L, "ARCHIVED", "已归档", "", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    CaseRecordStatus(
            Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    @Override
    public Long getCodeSystemId() {
        return codeSystemId;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDisplay() {
        return display;
    }

    @Override
    public String getDefinition() {
        return definition;
    }

    @Override
    public Long getSortOrder() {
        return sortOrder;
    }
}
