/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.servicerequestext.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 体检服务申请执行扩展读模型。
 */
@Getter
@Setter
public class PeisServiceRequestExtModel {

    /** 服务申请主键。 */
    private Long serviceRequestId;

    /** 就诊主键（来自 SR）。 */
    private Long encounterId;

    /** 项目 ActivityDefinition 主键（来自 SR）。 */
    private Long activityId;

    /** 执行状态。 */
    private String execStatusEnum;

    /** LIS 外发状态。 */
    private String lisDispatchEnum;

    /** PACS 外发状态。 */
    private String pacsDispatchEnum;
}
