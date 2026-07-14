/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.refundapplication.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DRA-02：提交前校验耗材执行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-申请退费-耗材执行门禁请求")
public class VerifyConsumableExecutionBeforeRefundRequest {

    /** 就诊主键。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 勾选收费项主键集合。 */
    @NotEmpty(message = "收费项标识列表不能为空")
    /** 勾选收费项主键集合。 */
    @Schema(description = "勾选收费项主键集合", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> chargeItemIds;
}
