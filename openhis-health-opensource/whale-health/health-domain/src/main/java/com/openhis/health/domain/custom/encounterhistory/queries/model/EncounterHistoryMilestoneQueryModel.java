/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.encounterhistory.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 里程碑列表查询模型。
 */
@Getter
@Setter
public class EncounterHistoryMilestoneQueryModel {
    /** 主键。 */
    private Long id;
    /** 名称。 */
    private String name;
    /** 就诊主键。 */
    private Long encounterId;
    /** 请求主键。 */
    private Long requestId;
    /** 请求来源表名称。 */
    private String requestTableName;
    /** 原始机构主键。 */
    private Long originalOrganizationId;
    /** 目标机构主键。 */
    private Long targetOrganizationId;
    /** 原始位置主键。 */
    private Long originalLocationId;
    /** 目标位置主键。 */
    private Long targetLocationId;
    /** out方式编码。 */
    private String outWayCode;
    /** created时间。 */
    private LocalDateTime createdAt;
}
