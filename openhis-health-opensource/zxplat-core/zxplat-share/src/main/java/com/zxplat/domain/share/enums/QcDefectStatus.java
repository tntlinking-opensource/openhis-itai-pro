/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 质控缺陷处理状态。
 */
public enum QcDefectStatus implements EnumInterface {
/** 待整改（QcDefectStatus，持久化编码 {@code open}）。 */
    OPEN(0L, "open", "待整改", "", 0L),
/** 已整改待复核（QcDefectStatus，持久化编码 {@code rectified}）。 */
    RECTIFIED(0L, "rectified", "已整改待复核", "", 1L),
/** 已关闭（QcDefectStatus，持久化编码 {@code closed}）。 */
    CLOSED(0L, "closed", "已关闭", "", 2L);

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
    QcDefectStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
