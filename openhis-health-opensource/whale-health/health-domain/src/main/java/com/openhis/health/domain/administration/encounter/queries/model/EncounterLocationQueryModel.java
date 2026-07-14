/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 就诊位置轨迹读模型：单条位置占用记录及起止时间。
 */
@Getter
@Setter
public class EncounterLocationQueryModel {
    /** 位置记录主键。 */
    private Long locationRecordId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 地点主键。 */
    private Long locationId;
    /** 地点形态枚举编码。 */
    private String formEnum;
    /** 占用状态枚举编码。 */
    private String statusEnum;
    /** 占用开始时间。 */
    private LocalDateTime startTime;
    /** 占用结束时间。 */
    private LocalDateTime endTime;
}
