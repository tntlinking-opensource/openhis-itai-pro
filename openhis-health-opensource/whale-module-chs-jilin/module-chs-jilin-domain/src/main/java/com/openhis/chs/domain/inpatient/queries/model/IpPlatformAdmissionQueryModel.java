/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inpatient.queries.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 住院就诊平台入院登记快照（列行enrichment） */
@Data
@Accessors(chain = true)
public class IpPlatformAdmissionQueryModel {

    private Long encounterId;
    private Long contractId;
    private String mdtrtId;
    private Boolean platformAdmitted;
    /** 参保地医保区划（来自住院预结算快照）。 */
    private String insuplcAdmdvs;
}
