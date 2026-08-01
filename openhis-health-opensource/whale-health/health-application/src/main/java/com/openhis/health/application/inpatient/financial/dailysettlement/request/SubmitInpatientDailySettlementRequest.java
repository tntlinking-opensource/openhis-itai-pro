/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.dailysettlement.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 确认住院交账请求（IF-IP-DCL-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "确认住院交账请求", requiredProperties = {"dateFrom", "dateTo", "orgId", "paymentIds"})
public class SubmitInpatientDailySettlementRequest {

    /** 日结时间起。 */
    @NotNull(message = "日结时间起不能为空")
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "日结时间起", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime dateFrom;

    /** 日结时间止。 */
    @NotNull(message = "日结时间止不能为空")
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "日结时间止", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime dateTo;

    /**
     * 多选收款人 Practitioner 主键（可选软筛；空=不按人过滤）。
     */
    @Schema(description = "多选收款人 Practitioner 主键列表（可选软筛）")
    private List<Long> cashierIds;

    /** 交账科室。 */
    @NotNull(message = "交账科室不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "交账科室", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long orgId;

    /** 收费窗口，可选。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费窗口")
    private Long locationId;

    /** 本次勾选挂载的 payment 主键（须 ⊆ 未交账候选；BR-IP-DCL-018）。 */
    @NotEmpty(message = "请至少选择一笔待交账流水")
    @Schema(description = "勾选 payment 主键列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<@NotNull Long> paymentIds;
}
