/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 就诊摘要读模型（发放工作台分页）。
 */
@Getter
@Setter
/**
 * EncounterInfoModel。
 */
public class EncounterInfoModel {

    /** 就诊主键。 */
    /**
     * encounterId。
     */
    private Long encounterId;

    /** 患者主键。 */
    /**
     * patientId。
     */
    private Long patientId;
}
