/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.lab.pathb;

import lombok.Getter;
import lombok.Setter;

/**
 * MedSync applyNo 反查结果：定位 Whale {@code ServiceRequest} 及就诊/患者上下文。
 */
@Getter
@Setter
public class YunlisApplyNoResolveResult {

    /** ServiceRequest 主键。 */
    private Long serviceRequestId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 命中方式：PRESCRIPTION_NO 或 SERVICE_REQUEST_ID。 */
    private String matchedBy;
}
