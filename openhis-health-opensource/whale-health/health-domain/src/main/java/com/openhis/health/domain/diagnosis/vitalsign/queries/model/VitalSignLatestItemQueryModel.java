/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.diagnosis.vitalsign.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 就诊下各字典项最新测量读模型（SDD §4.6.2，供 getLatestByEncounterId）。
 */
@Getter
@Setter
public class VitalSignLatestItemQueryModel {

    /** 术语体系 code。 */
    private String termCode;

    /** 字典项 code。 */
    private String itemCode;

    /** 最新测量值。 */
    private String itemValue;

    /** 计量单位。 */
    private String units;

    /** 该字典项最新记录时点。 */
    private LocalDateTime recordedAt;
}
