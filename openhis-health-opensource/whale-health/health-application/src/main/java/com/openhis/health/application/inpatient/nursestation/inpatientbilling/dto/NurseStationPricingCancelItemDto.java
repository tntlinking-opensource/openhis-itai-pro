/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 取消划价单行入参（IF-IP-PRC-06，与已划价列表 {@code NurseStationPricedOrderRowDto} 对齐）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "取消划价单行")
public class NurseStationPricingCancelItemDto {

    /** 医嘱类型：SERVICE_REQUEST / DEVICE_REQUEST。 */
    @Schema(description = "医嘱类型：SERVICE_REQUEST / DEVICE_REQUEST", requiredMode = Schema.RequiredMode.REQUIRED)
    private String orderType;

    /** 请求主键。 */
    @Schema(description = "请求主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestId;

    /** 就诊主键。 */
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;
}
