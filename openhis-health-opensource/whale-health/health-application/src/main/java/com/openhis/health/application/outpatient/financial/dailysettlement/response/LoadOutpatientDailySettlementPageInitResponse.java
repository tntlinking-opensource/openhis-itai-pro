/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.dailysettlement.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 门诊日结页初始化响应（IF-OP-DCL-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊日结页初始化响应")
public class LoadOutpatientDailySettlementPageInitResponse {

    /** 默认结账日起（yyyy-MM-dd）。 */
    @Schema(description = "默认结账日起")
    private String defaultDateFrom;

    /** 默认结账日止（yyyy-MM-dd）。 */
    @Schema(description = "默认结账日止")
    private String defaultDateTo;

    /** 自费/医保筛选项。 */
    @Schema(description = "费用性质筛选项")
    private List<OptionDto> expensePropertyOptions;

    /** 是否允许代指定收费员交账。 */
    @Schema(description = "是否允许财务代交账")
    private Boolean financialDelegateEnabled;

    /** 经办人下拉；非财务仅含本人。 */
    @Schema(description = "经办人下拉")
    private List<OptionDto> cashierOptions;

    /** 当前操作员对应 Practitioner 主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "当前经办人 Practitioner 主键")
    private Long currentCashierId;
}
