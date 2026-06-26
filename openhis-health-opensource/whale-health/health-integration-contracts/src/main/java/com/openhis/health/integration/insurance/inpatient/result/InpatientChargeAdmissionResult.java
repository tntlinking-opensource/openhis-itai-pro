/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2401 入院登记结果。 */
@Data
@Accessors(chain = true)
public class InpatientChargeAdmissionResult {

    /** 平台就诊 ID mdtrt_id。 */
    private String encounterPlatformId;
}
