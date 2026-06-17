/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.nursestation.skintest.response;

import lombok.Getter;
import lombok.Setter;

/**
 * IF-OP-STK-03 保存皮试结果响应。
 */
@Getter
@Setter
public class SaveOutpatientSkinTestRecordResponse {

    private Long allergyRecordId;
    private Long skinTestServiceRequestId;
}
