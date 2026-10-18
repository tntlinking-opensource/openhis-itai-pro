/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.diagnosis.vitalsign.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 生命体征主表读模型（DM-DIA-002 v2.1 §4.6.1）。
 */
@Getter
@Setter
public class VitalSignHeaderQueryModel {

    /** 主表 id。 */
    private Long id;

    /** 患者 id。 */
    private Long patientId;

    /** 就诊 id。 */
    private Long encounterId;

    /** 记录时点。 */
    private LocalDateTime recordedAt;

    /** 单据类型 code。 */
    private String typeEnum;

    /** 业务单号。 */
    private String busNo;

    /** 病床 id。 */
    private Long locationId;

    /** 婴儿序号。 */
    private Integer babyNo;

    /** 记录人 id。 */
    private Long recorderId;

    /** 备注。 */
    private String remark;
}
