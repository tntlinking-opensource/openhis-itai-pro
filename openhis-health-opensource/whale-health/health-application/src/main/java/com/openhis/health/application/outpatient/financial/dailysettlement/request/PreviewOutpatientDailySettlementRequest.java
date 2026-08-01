/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.dailysettlement.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 门诊日结查询请求（IF-OP-DCL-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊日结查询请求", requiredProperties = {"dateFrom", "dateTo", "orgId"})
public class PreviewOutpatientDailySettlementRequest {

    /** 日结时间起（含）。 */
    @NotNull(message = "日结时间起不能为空")
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "日结时间起", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime dateFrom;

    /** 日结时间止（含）。 */
    @NotNull(message = "日结时间止不能为空")
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "日结时间止", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime dateTo;

    /** 交账科室（收款日结范围键 org_id）。 */
    @NotNull(message = "交账科室不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "交账科室", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long orgId;

    /**
     * 多选收款人 Practitioner 主键（可选展示软筛；空表示不按人过滤）。
     */
    @Schema(description = "多选收款人 Practitioner 主键列表（可选软筛）")
    private List<Long> cashierIds;

    /** 费用性质展示过滤：selfPay / insurance / fin_contract.bus_no；空表示全部。 */
    @Schema(description = "费用性质展示过滤：空=全部")
    private String expenseProperty;
}
