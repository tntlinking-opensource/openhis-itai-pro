/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 住院就诊平台入院登记快照（列表 enrichment）。 */
@Data
@Accessors(chain = true)
public class IpInsurancePlatformAdmissionSnapshot {

    private Long encounterId;
    private Long contractId;
    private String mdtrtId;
    private Boolean platformAdmitted;
}
