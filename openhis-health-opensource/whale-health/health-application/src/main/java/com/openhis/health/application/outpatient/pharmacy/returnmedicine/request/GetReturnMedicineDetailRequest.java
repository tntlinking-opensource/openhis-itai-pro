/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.returnmedicine.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 查询退药明细请求（IF-PHA-02-03）。
 */
@Data
/** 查询退药明细请求。 */
@Schema(description = "查询退药明细请求")
public class GetReturnMedicineDetailRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 退药状态过滤（PENDING_REFUND/REFUNDED）；空表示不过滤。 */
    @Schema(description = "退药状态过滤（PENDING_REFUND/REFUNDED）；空表示不过滤")
    private String refundStatus;
}
