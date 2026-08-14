/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 日结下半区：按 {@code fin_contract.bus_no}（费用性质）动态汇总块。
 */
@Data
@Accessors(chain = true)
@Schema(description = "日结费用性质汇总")
public class DailySettlementExpenseNatureSummaryDto {

    /** 合同业务号（payment.contract_no）。 */
    @Schema(description = "费用性质业务号")
    private String contractNo;

    /** 费用性质名称。 */
    @Schema(description = "费用性质名称")
    private String contractName;

    /** 是否医保费用性质。 */
    @Schema(description = "是否医保")
    private Boolean insurance;

    /** 人次（patientId 去重）。 */
    @Schema(description = "人次")
    private Integer personCount;

    /** 现金（自付现金渠道）。 */
    @Schema(description = "现金")
    private String cashAmount;

    /** 统筹（仅医保）。 */
    @Schema(description = "医保统筹")
    private String fundAmount;

    /** 个账（仅医保）。 */
    @Schema(description = "个账")
    private String accountAmount;

    /** 基金（统筹口径合计，仅医保）。 */
    @Schema(description = "基金")
    private String fundTotalAmount;

    /** 总额：医保=统筹+个账；自费=流水净额代数和。 */
    @Schema(description = "总额")
    private String totalAmount;

    /** 医保合同下按险种拆分；自费为空列表。 */
    @Schema(description = "险种类型汇总")
    private List<DailySettlementInsuranceTypeSummaryDto> insuranceTypeSummaries = new ArrayList<>();
}
