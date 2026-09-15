/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.clinical.allergyhistory.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 登记「无已知过敏」请求。
 */
@Getter
@Setter
public class SavePatientNkdaRequest {

    /** 患者主键。 */
    private Long patientId;

    /** 当前就诊主键。 */
    private Long encounterId;

    /** 验证状态 code。 */
    private String verificationStatusCode;
}
