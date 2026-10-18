/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 就诊当前位置信息查询模型。
 */
@Getter
@Setter
public class EncounterLocationInfoQueryModel {

    /**
     * 就诊标识。
     */
    private Long encounterId;

    /**
     * 当前活动位置标识。
     */
    private Long currentLocationId;

    /**
     * 当前活动位置形态编码。
     */
    private String currentFormEnum;

    /**
     * 当前活动位置开始时间。
     */
    private LocalDateTime activeSince;
}
