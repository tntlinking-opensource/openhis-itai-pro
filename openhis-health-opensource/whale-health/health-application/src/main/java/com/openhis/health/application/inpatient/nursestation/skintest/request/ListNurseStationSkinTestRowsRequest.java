/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.skintest.request;

import lombok.Getter;
import lombok.Setter;

/**
 * IF-IP-STK-02 查询就诊皮试列表请求。
 */
@Getter
@Setter
public class ListNurseStationSkinTestRowsRequest {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键（与就诊一致性 Gate）。 */
    private Long patientId;
}
