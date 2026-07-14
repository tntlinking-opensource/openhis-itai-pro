/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.inpatient;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 入院体征写编排上下文：就诊、时点、床位与提交块。
 */
@Getter
@Setter
@Accessors(chain = true)
public class AdmissionVitalSignSaveContext {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 记录时点（入科后须与 {@code adm_encounter.start_time} 一致）。 */
    private LocalDateTime recordedAt;

    /** 记录时病床 {@code Location.id}，未分床时可空。 */
    private Long locationId;

    /** 操作人（审计）。 */
    private Long operatorId;

    /** 记录人 {@code Practitioner.id}，可空。 */
    private Long recorderId;

    /** 前端提交块；空字段不覆盖库内已有值（对齐老系统 merge 语义）。 */
    private AdmissionVitalSignSubmitDto submitDto;
}
