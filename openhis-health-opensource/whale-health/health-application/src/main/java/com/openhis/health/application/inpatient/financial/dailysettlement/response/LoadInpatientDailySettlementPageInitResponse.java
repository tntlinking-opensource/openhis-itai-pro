/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.dailysettlement.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院日结页初始化响应（IF-IP-DCL-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院日结页初始化响应")
public class LoadInpatientDailySettlementPageInitResponse {

    /** 默认结账日起。 */
    @Schema(description = "默认结账日起")
    private String defaultDateFrom;

    /** 默认结账日止。 */
    @Schema(description = "默认结账日止")
    private String defaultDateTo;

    /** 费用性质筛选项。 */
    @Schema(description = "费用性质筛选项")
    private List<OptionDto> expensePropertyOptions;

    /** 收款人下拉岗池：仅 ROLE_CASHIER；查询后再合并窗内收费人。 */
    @Schema(description = "收款人下拉")
    private List<OptionDto> cashierOptions;

    /** 当前经办人 Practitioner 主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "当前经办人 Practitioner 主键")
    private Long currentCashierId;

    /**
     * 本院是否启用收费折扣能力（BR-IP-DCL-031）；false 时日结不展示折扣合计列。
     */
    @Schema(description = "是否启用收费折扣")
    private Boolean discountFeatureEnabled;
}
