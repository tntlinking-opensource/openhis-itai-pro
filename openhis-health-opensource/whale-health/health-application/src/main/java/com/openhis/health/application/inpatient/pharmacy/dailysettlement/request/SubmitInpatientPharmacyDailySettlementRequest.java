/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dailysettlement.request;

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
 * 确认住院药房交账请求（IF-IP-PDC-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "确认住院药房交账请求", requiredProperties = {"dateFrom", "dateTo", "locationId", "selectedDispenseIds"})
public class SubmitInpatientPharmacyDailySettlementRequest {

    /** 业务窗起（与预览相同）。 */
    @NotNull(message = "日结时间起不能为空")
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "日结时间起", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime dateFrom;

    /** 业务窗止（与预览相同）。 */
    @NotNull(message = "日结时间止不能为空")
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "日结时间止", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime dateTo;

    /** 发药库房。 */
    @NotNull(message = "药房不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "发药库房主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long locationId;

    /** 本次勾选挂载的发药行主键（须 ⊆ 未交账候选）。 */
    @NotEmpty(message = "请至少选择一笔待交账发药明细")
    @Schema(description = "勾选发药行主键列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<@NotNull Long> selectedDispenseIds;
}
