/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.encounterhistory.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 最近一次转科上下文查询模型。
 */
@Getter
@Setter
public class LatestTransferContextQueryModel {
    /** 就诊历史主键。 */
    private Long encounterHistoryId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 原始机构主键。 */
    private Long originalOrganizationId;
    /** 目标机构主键。 */
    private Long targetOrganizationId;
    /** 原始位置主键。 */
    private Long originalLocationId;
    /** 目标位置主键。 */
    private Long targetLocationId;
    /** created时间。 */
    private LocalDateTime createdAt;
}
