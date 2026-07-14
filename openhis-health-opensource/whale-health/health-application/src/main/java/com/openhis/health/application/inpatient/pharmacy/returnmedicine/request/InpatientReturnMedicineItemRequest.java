/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.returnmedicine.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 住院确认退药单项请求（IF-IPR-04）。
 */
@Data
/** 住院确认退药单项请求。 */
@Schema(description = "住院确认退药单项请求", requiredProperties = {"dispenseId"})
public class InpatientReturnMedicineItemRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 待确认退药发放行主键；须 PENDING_REFUND 且库存/追溯补偿可写。 */
    @Schema(description = "待退发放行主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "dispenseId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;
}
