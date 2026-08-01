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
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 住院药房日结查询请求（IF-IP-PDC-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院药房日结查询请求", requiredProperties = {"dateFrom", "dateTo", "locationId"})
public class PreviewInpatientPharmacyDailySettlementRequest {

    /** 业务窗起（含）。 */
    @NotNull(message = "日结时间起不能为空")
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "日结时间起", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime dateFrom;

    /** 业务窗止（含）。 */
    @NotNull(message = "日结时间止不能为空")
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "日结时间止", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime dateTo;

    /** 发药库房（单药房范围键）。 */
    @NotNull(message = "药房不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "发药库房主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long locationId;

    /**
     * 发药人软筛（Practitioner 主键）；空或 null 表示不按人过滤。
     * 漏交扫描不使用本字段。
     */
    @Schema(description = "发药人主键列表（软筛）")
    private List<Long> practitionerIds;
}
