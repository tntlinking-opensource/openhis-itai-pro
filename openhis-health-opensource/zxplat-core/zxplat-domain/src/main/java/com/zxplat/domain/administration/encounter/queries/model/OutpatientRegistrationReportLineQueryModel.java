/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.queries.model;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊挂号明细报表行（Encounter 本聚合字段；费用性质由应用层 enrich）。
 */
@Getter
@Setter
public class OutpatientRegistrationReportLineQueryModel {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 门诊号。 */
    private String busNo;

    /** 挂号科室。 */
    private Long organizationId;

    /** 号源类型（HealthcareService 主键）。 */
    private Long serviceTypeId;

    /** 就诊状态码。 */
    private String statusEnum;

    /** 挂号发生时刻（coalesce startTime/createdAt）。 */
    private LocalDateTime registrationTime;

    /** 挂号医生主键（活动 REGISTRATION_DOCTOR；可空）。 */
    private Long registrationDoctorId;
}
