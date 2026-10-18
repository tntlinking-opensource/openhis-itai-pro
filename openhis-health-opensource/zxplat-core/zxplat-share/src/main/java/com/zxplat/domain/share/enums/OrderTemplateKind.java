/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 组套模板种类（通用 / 中医 / 划价 / 申请单）。
 */
public enum OrderTemplateKind implements EnumInterface {
    /**
     * 通用组套（GENERAL）。
     */
    GENERAL(0L, "GENERAL", "通用组套", "", 1L),
    /**
     * 中医协定处方/中药方案组套。
     */
    TCM(0L, "TCM", "中医组套", "", 2L),
    /**
     * 护士站划价组套（行仅 SERVICE/DEVICE）。
     */
    PRICING(0L, "PRICING", "划价组套", "", 3L),
    /**
     * 医生站申请单组套（行仅检查/检验/治疗诊疗项目，类别记在行 groupKey）。
     */
    SERVICE_APPLY(0L, "SERVICE_APPLY", "申请单组套", "", 4L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    OrderTemplateKind(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
