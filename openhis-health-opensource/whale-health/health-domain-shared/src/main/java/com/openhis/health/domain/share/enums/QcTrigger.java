/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 质控执行触发来源。
 */
public enum QcTrigger implements EnumInterface {
/** 接收时（QcTrigger，持久化编码 {@code on_receive}）。 */
    ON_RECEIVE(0L, "on_receive", "接收时", "", 0L),
/** 归档前（QcTrigger，持久化编码 {@code before_archive}）。 */
    BEFORE_ARCHIVE(0L, "before_archive", "归档前", "", 1L),
/** 手动（QcTrigger，持久化编码 {@code manual}）。 */
    MANUAL(0L, "manual", "手动", "", 2L),
/** 定时（QcTrigger，持久化编码 {@code scheduled}）。 */
    SCHEDULED(0L, "scheduled", "定时", "", 3L);

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
    QcTrigger(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
