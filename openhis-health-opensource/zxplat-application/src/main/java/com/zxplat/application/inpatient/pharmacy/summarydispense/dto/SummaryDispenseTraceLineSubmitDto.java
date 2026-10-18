/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.summarydispense.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 汇总核发追溯码提交行（IF-IPS-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "汇总核发追溯码提交行")
public class SummaryDispenseTraceLineSubmitDto {

    /** 汇总 Supply 行主键 {@code wkf_supply_request.id}，与 IF-IPS-02 合并行一一对应。 */
    /** 汇总 Supply 行主键；追溯码核发后写入对应 {@code wkf_supply_delivery.trace_no}。 */
    @Schema(description = "供应请求行主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long supplyRequestId;

    /** 追溯码串（逗号分隔；落 {@code wkf_supply_delivery.trace_no}，不关联患者发放行）。 */
    @Schema(description = "追溯码串，逗号分隔")
    private String traceNo;
}
