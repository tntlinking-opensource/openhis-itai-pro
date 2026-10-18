/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 病案状态迁移动作（工作流入口）。
 */
public enum CaseRecordTransitionAction implements EnumInterface {
/** 创建（CaseRecordTransitionAction，持久化编码 {@code create}）。 */
    CREATE(0L, "create", "创建", "", 0L),
/** 提交（CaseRecordTransitionAction，持久化编码 {@code submit}）。 */
    SUBMIT(0L, "submit", "提交", "", 1L),
/** 接收（CaseRecordTransitionAction，持久化编码 {@code receive}）。 */
    RECEIVE(0L, "receive", "接收", "", 2L),
/** 退回（CaseRecordTransitionAction，持久化编码 {@code return}）。 */
    RETURN(0L, "return", "退回", "", 3L),
/** 开始质控（CaseRecordTransitionAction，持久化编码 {@code startQc}）。 */
    START_QC(0L, "startQc", "开始质控", "", 4L),
/** 归档（CaseRecordTransitionAction，持久化编码 {@code archive}）。 */
    ARCHIVE(0L, "archive", "归档", "", 5L),
/** 解档（CaseRecordTransitionAction，持久化编码 {@code unarchive}）。 */
    UNARCHIVE(0L, "unarchive", "解档", "", 6L);

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
    CaseRecordTransitionAction(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
