/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionIn;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

/** 人员累计信息查询命令（平台 5206 语义的中性封装）。 */
@Data
@Accessors(chain = true)
public class InsuranceCumulativeQueryExtensionIn {

    /** 费用性质 id。 */
    private Long contractId;
    /** 人员编号。 */
    private String psnNo;
    /** 参保地医保区划（可选）。 */
    @JsonProperty("insuplcAdmdvs")
    private String insurancePoolAreaCode;
    /** 累计年月 yyyyMM；空则查全部有效累计。 */
    private String cumYm;
}
