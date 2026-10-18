/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 机构分类枚举。
 */
public enum OrganizationClassification implements EnumInterface {
    /** 门诊（OrganizationClassification，编码 {@code CLINIC}）。 */
    CLINIC(0L, "CLINIC", "门诊", "", 1L),
    /** 住院（OrganizationClassification，编码 {@code INPATIENT}）。 */
    INPATIENT(0L, "INPATIENT", "住院", "", 2L),
    /** 药房（OrganizationClassification，编码 {@code PHARMACY}）。 */
    PHARMACY(0L, "PHARMACY", "药房", "", 3L),
    /** 库房（OrganizationClassification，编码 {@code STORAGE}）。 */
    STORAGE(0L, "STORAGE", "库房", "", 4L),
    /** 财务（OrganizationClassification，编码 {@code FIN}）。 */
    FIN(0L, "FIN", "财务", "", 5L),
    /** 护士站（OrganizationClassification，编码 {@code NS}）。 */
    NS(0L, "NS", "护士站", "", 6L),
    /** 管理（OrganizationClassification，编码 {@code MANAGER}）。 */
    MANAGER(0L, "MANAGER", "管理", "", 7L),
    /** 后勤支持（OrganizationClassification，编码 {@code SUPPORT}）。 */
    SUPPORT(0L, "SUPPORT", "后勤支持", "", 8L),
    /** 其他（OrganizationClassification，编码 {@code OTHER}）。 */
    OTHER(0L, "OTHER", "其他", "", 9L),
    /** 医技（OrganizationClassification，编码 {@code TECH}）。 */
    TECH(0L, "TECH", "医技", "", 10L),
    /** 急诊（OrganizationClassification，编码 {@code EMERGENCY}）：急诊科业务，在急诊系统执行，不进护士站/医技工作站。 */
    EMERGENCY(0L, "EMERGENCY", "急诊", "", 11L),
    /** 体检（OrganizationClassification，编码 {@code PEIS}）：体检科业务，在体检系统执行，不进护士站/医技工作站。 */
    PEIS(0L, "PEIS", "体检", "", 12L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    OrganizationClassification(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
