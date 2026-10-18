/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.benefit.ExtensionIn;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

/** 慢特病备案查询命令（平台 5301 语义的中性封装）。 */
@Data
@Accessors(chain = true)
public class InsuranceOpspFilingQueryExtensionIn {

    /** 费用性质 id。 */
    private Long contractId;
    /** 人员编号。 */
    private String psnNo;
    /** 参保地医保区划（可选）。 */
    @JsonProperty("insuplcAdmdvs")
    private String insurancePoolAreaCode;
}
