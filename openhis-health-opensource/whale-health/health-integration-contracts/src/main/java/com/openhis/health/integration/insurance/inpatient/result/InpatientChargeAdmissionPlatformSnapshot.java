/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 住院就诊平台入院登记快照（列表 enrichment / 待入科医保退入院判定）。 */
@Data
@Accessors(chain = true)
public class InpatientChargeAdmissionPlatformSnapshot {

    private Long encounterId;
    private Long contractId;
    private String mdtrtId;
    private Boolean platformAdmitted;
}
