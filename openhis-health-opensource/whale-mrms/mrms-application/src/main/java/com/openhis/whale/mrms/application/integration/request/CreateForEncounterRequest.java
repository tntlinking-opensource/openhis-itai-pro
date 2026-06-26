/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.integration.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 离院建档请求（集成/测试用）。
 */
@Getter
@Setter
public class CreateForEncounterRequest {

    /** 租户主键。 */
    private Long tenantId;

    /** 患者主键。 */
    private Long patientId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 操作人主键。 */
    private Long operatorId;
}
