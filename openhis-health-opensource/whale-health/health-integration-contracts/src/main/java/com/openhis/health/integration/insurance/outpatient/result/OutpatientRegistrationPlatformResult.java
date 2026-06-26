/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.outpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2201 挂号平台登记结果。 */
@Data
@Accessors(chain = true)
public class OutpatientRegistrationPlatformResult {

    /** 平台就诊 ID mdtrt_id。 */
    private String encounterPlatformId;
}
