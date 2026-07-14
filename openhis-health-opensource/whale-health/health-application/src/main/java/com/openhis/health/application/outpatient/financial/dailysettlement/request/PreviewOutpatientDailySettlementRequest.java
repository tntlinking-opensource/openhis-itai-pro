/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.dailysettlement.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 门诊日结查询请求（IF-OP-DCL-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊日结查询请求", requiredProperties = {"dateFrom", "dateTo"})
public class PreviewOutpatientDailySettlementRequest {

    /** 业务日起（闭区间）。 */
    @NotNull(message = "业务日起不能为空")
    @Schema(description = "业务日起", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate dateFrom;

    /** 业务日止（闭区间）。 */
    @NotNull(message = "业务日止不能为空")
    @Schema(description = "业务日止", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate dateTo;

    /** 经办人 Practitioner 主键；缺省为当前操作员。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "经办人 Practitioner 主键")
    private Long cashierId;

    /** 费用性质展示过滤：selfPay / insurance。 */
    @Schema(description = "费用性质展示过滤：selfPay / insurance")
    private String expenseProperty;
}
