/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.clinical.allergyhistory.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 删除主诉过敏史或 NKDA 请求。
 */
@Getter
@Setter
public class DeletePatientAllergyHistoryRequest {

    /** 患者主键。 */
    private Long patientId;

    /** 当前就诊主键。 */
    private Long encounterId;

    /** 过敏记录主键。 */
    private Long allergyRecordId;
}
