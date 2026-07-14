/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 职业类型枚举（患者/医务人员档案中的职业分类，对应术语编码系统）。
 */
public enum OccupationType implements EnumInterface {
    /** 国家公务员（OccupationType，编码 {@code STATE_CIVIL_SERVANT}）。 */
    STATE_CIVIL_SERVANT(0L, "STATE_CIVIL_SERVANT", "国家公务员", "", 11L),
    /** 专业技术人员（OccupationType，编码 {@code PROFESSIONAL}）。 */
    PROFESSIONAL(0L, "PROFESSIONAL", "专业技术人员", "", 13L),
    /** 职员（OccupationType，编码 {@code CLERK}）。 */
    CLERK(0L, "CLERK", "职员", "", 17L),
    /** 企业管理人员（OccupationType，编码 {@code BUSINESS_MANAGER}）。 */
    BUSINESS_MANAGER(0L, "BUSINESS_MANAGER", "企业管理人员", "", 21L),
    /** 工人（OccupationType，编码 {@code WORKER}）。 */
    WORKER(0L, "WORKER", "工人", "", 24L),
    /** 农民（OccupationType，编码 {@code FARMER}）。 */
    FARMER(0L, "FARMER", "农民", "", 27L),
    /** 学生（OccupationType，编码 {@code STUDENT}）。 */
    STUDENT(0L, "STUDENT", "学生", "", 31L),
    /** 现役军人（OccupationType，编码 {@code ACTIVE_MILITARY}）。 */
    ACTIVE_MILITARY(0L, "ACTIVE_MILITARY", "现役军人", "", 37L),
    /** 自由职业者（OccupationType，编码 {@code FREELANCER}）。 */
    FREELANCER(0L, "FREELANCER", "自由职业者", "", 51L),
    /** 个体经营者（OccupationType，编码 {@code SELF_EMPLOYED}）。 */
    SELF_EMPLOYED(0L, "SELF_EMPLOYED", "个体经营者", "", 54L),
    /** 无业人员（OccupationType，编码 {@code UNEMPLOYED}）。 */
    UNEMPLOYED(0L, "UNEMPLOYED", "无业人员", "", 70L),
    /** 退（离）休人员（OccupationType，编码 {@code RETIREE}）。 */
    RETIREE(0L, "RETIREE", "退（离）休人员", "", 80L),
    /** 其他（OccupationType，编码 {@code OTHER}）。 */
    OTHER(0L, "OTHER", "其他", "", 90L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    OccupationType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
