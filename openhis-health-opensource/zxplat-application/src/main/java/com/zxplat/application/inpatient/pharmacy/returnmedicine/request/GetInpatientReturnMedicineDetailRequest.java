/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.returnmedicine.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 查询住院退药明细请求（IF-IPR-03）。
 */
@Data
/** 查询住院退药明细请求。 */
@Schema(description = "查询住院退药明细请求", requiredProperties = {"encounterId"})
public class GetInpatientReturnMedicineDetailRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 住院就诊主键；明细须与队列选中 encounter 一致。 */
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "encounterId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 退药状态过滤：PENDING_REFUND / REFUNDED；空表示默认子集。 */
    @Schema(description = "退药状态过滤：PENDING_REFUND / REFUNDED；空表示默认子集")
    private String refundStatus;
}
