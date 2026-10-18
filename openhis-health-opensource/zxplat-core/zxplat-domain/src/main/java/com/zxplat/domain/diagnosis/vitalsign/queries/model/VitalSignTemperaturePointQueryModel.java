/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.diagnosis.vitalsign.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 体温单曲线点读模型（仅主表，DM-DIA-002 v2.0 §4.6.2）。
 */
@Getter
@Setter
public class VitalSignTemperaturePointQueryModel {

    /** 主表 id。 */
    private Long id;

    /** 记录时点。 */
    private LocalDateTime recordedAt;

    /** 体温。 */
    private String temperature;

    /** 单据类型 code。 */
    private String typeEnum;
}
