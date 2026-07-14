/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** 挂号页费用性质医保通道概要。 */
@Data
@Accessors(chain = true)
@Schema(description = "费用性质医保通道概要")
public class ContractInsuranceProfileDto {

    /** 费用性质主键。 */
    @Schema(description = "费用性质主键")
    private String contractId;

    /** 类别码（ChargeTypeCategory 1~5）。 */
    @Schema(description = "类别码（ChargeTypeCategory 1~5）")
    private String categoryCode;

    /** 合同业务号 bus_no（对接 chs-contract）。 */
    @Schema(description = "合同业务号 bus_no")
    private String busNo;

    /** 结算统筹区 adm_vs。 */
    @Schema(description = "结算统筹区 adm_vs")
    private String treatmentAreaCode;

    /** 是否医保类费用性质。 */
    @Schema(description = "是否医保标记")
    private Boolean ybFlag;
}
