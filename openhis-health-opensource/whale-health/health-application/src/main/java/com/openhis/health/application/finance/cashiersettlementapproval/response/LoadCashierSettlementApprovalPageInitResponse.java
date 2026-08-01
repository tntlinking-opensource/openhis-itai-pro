/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.cashiersettlementapproval.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 交账审批页初始化响应（IF-CS-APR-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "交账审批页初始化响应")
public class LoadCashierSettlementApprovalPageInitResponse {

    /** 费用/药房大类选项。 */
    @Schema(description = "费用/药房大类选项")
    private List<OptionDto> categoryOptions = new ArrayList<>();

    /** 交账类型选项（四码）。 */
    @Schema(description = "交账类型选项")
    private List<OptionDto> typeOptions = new ArrayList<>();

    /** 审批状态选项。 */
    @Schema(description = "审批状态选项")
    private List<OptionDto> statusOptions = new ArrayList<>();

    /** 药房下拉（药房日结筛选；空选=不限药房）。 */
    @Schema(description = "药房下拉选项")
    private List<OptionDto> pharmacyOptions = new ArrayList<>();
}
