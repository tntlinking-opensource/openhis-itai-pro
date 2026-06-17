/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.nursestation.skintest.request;

import lombok.Getter;
import lombok.Setter;

/**
 * IF-OP-STK-02 查询就诊皮试列表请求。
 */
@Getter
@Setter
public class ListOutpatientSkinTestRowsRequest {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键（与就诊一致性 Gate）。 */
    private Long patientId;
}
