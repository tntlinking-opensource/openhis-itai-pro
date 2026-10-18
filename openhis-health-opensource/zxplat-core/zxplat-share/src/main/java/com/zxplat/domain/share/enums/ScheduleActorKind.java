/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;
/**
 * 号源日程参与者类型（DM-WKF-009 §4.1）。
 */
public enum ScheduleActorKind implements EnumInterface {
    /** 医务人员（ScheduleActorKind，编码 {@code PRACTITIONER}）。 */
    PRACTITIONER(0L, "PRACTITIONER", "Practitioner", "", 10L),
    /** 医务人员角色（ScheduleActorKind，编码 {@code PRACTITIONER_ROLE}）。 */
    PRACTITIONER_ROLE(0L, "PRACTITIONER_ROLE", "PractitionerRole", "", 11L),
    /** 地点/科室（ScheduleActorKind，编码 {@code LOCATION}）。 */
    LOCATION(0L, "LOCATION", "Location", "", 20L),
    /** 医疗服务（ScheduleActorKind，编码 {@code HEALTHCARE_SERVICE}）。 */
    HEALTHCARE_SERVICE(0L, "HEALTHCARE_SERVICE", "HealthcareService", "", 30L),
    /** 设备（ScheduleActorKind，编码 {@code DEVICE}）。 */
    DEVICE(0L, "DEVICE", "Device", "", 40L),
    /** 患者（ScheduleActorKind，编码 {@code PATIENT}）。 */
    PATIENT(0L, "PATIENT", "Patient", "", 50L),
    /** 关联人员/联系人（ScheduleActorKind，编码 {@code RELATED_PERSON}）。 */
    RELATED_PERSON(0L, "RELATED_PERSON", "RelatedPerson", "", 60L),
    /** 照护团队（ScheduleActorKind，编码 {@code CARE_TEAM}）。 */
    CARE_TEAM(0L, "CARE_TEAM", "CareTeam", "", 70L),
    /** 组织机构（ScheduleActorKind，编码 {@code ORGANIZATION}）。 */
    ORGANIZATION(0L, "ORGANIZATION", "Organization", "", 80L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    ScheduleActorKind(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
