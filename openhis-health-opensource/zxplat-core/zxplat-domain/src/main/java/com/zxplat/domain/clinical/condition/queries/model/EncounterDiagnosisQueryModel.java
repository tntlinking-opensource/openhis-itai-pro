/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.condition.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 就诊诊断查询模型。
 */
@Getter
@Setter
public class EncounterDiagnosisQueryModel {
    /** 病情主键。 */
    private Long conditionId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 目录主键。 */
    private Long definitionId;
    /** description。 */
    private String description;
    /** 记录datetime。 */
    private LocalDateTime recordedDatetime;
}
