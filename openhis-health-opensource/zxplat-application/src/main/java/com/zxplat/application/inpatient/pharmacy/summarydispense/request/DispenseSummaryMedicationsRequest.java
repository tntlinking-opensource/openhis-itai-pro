/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.summarydispense.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.inpatient.pharmacy.summarydispense.dto.SummaryDispenseTraceLineSubmitDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量汇总核发请求（IF-IPS-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "批量汇总核发 IF-IPS-03", requiredProperties = {"summaryNos", "receiverId", "preparerPractitionerId"})
public class DispenseSummaryMedicationsRequest {

    /** 待核发汇总单号列表（同号 Supply 须全 APPROVAL、发放行须全 SUMMARIZED；批上限见 AS）。 */
    @Schema(description = "待核发汇总单号列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> summaryNos;

    /** 领药人主键（写入 SupplyDelivery.receiver_id）。 */
    @Schema(description = "领药人主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long receiverId;

    /** 调配药师主键（不得与当前发药药师同人）。 */
    @Schema(description = "调配药师主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long preparerPractitionerId;

    /** 核发时补录/覆盖追溯码（可选）。 */
    @Schema(description = "核发时补录或覆盖追溯码")
    private List<SummaryDispenseTraceLineSubmitDto> traceLines;
}
