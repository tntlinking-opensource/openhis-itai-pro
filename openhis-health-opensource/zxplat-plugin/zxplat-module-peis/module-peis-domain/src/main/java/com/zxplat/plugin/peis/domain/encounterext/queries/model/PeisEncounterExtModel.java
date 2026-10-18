/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.encounterext.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 体检就诊扩展读模型。
 */
@Getter
@Setter
public class PeisEncounterExtModel {

    /** 就诊主键。 */
    private Long encounterId;

    /** 套餐 ActivityDefinition 主键。 */
    private Long packageActivityId;

    /** 套餐快照 JSON。 */
    private String packageSnapshotJson;

    /** 是否团检。 */
    private Boolean groupFlag;

    /** 团检单位。 */
    private Long companyId;

    /** 团检合同。 */
    private Long contractId;

    /** 团检批次。 */
    private Long groupBatchId;

    /** 预约主键。 */
    private Long bookingAppointmentId;

    /** 体检流水号。 */
    private String busNo;

    /** 本次体检推荐人。 */
    private String referrerName;

    /** 本次体检是否提供早餐。 */
    private Boolean breakfastFlag;
}
