/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.dailysettlement.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 住院日结查询请求（IF-IP-DCL-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院日结查询请求", requiredProperties = {"dateFrom", "dateTo"})
public class PreviewInpatientDailySettlementRequest {

    /** 业务日起。 */
    @NotNull(message = "业务日起不能为空")
    @Schema(description = "业务日起", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate dateFrom;

    /** 业务日止。 */
    @NotNull(message = "业务日止不能为空")
    @Schema(description = "业务日止", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate dateTo;

    /** 经办人 Practitioner 主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "经办人 Practitioner 主键")
    private Long cashierId;

    /** 费用性质展示过滤。 */
    @Schema(description = "费用性质展示过滤：selfPay / insurance")
    private String expenseProperty;
}
