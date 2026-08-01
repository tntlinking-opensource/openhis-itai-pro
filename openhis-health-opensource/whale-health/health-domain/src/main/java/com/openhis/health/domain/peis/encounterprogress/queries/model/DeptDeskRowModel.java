/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.encounterprogress.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 分科诊台待检行读模型（DM-PEIS-008 §4.6.1）。
 */
@Getter
@Setter
public class DeptDeskRowModel {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 患者姓名。 */
    private String patientName;

    /** 体检流水号。 */
    private String busNo;

    /** 待检服务申请主键。 */
    private Long serviceRequestId;

    /** 项目展示名。 */
    private String activityDisplay;

    /** 执行状态 code。 */
    private String execStatusEnum;

    /** LIS 外发状态 code。 */
    private String lisDispatchEnum;
}
