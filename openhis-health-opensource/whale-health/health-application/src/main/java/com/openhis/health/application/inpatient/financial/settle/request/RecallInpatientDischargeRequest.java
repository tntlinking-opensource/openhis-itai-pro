/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院出院召回请求（AS-IP-FIN-004 召回写；收银台已结算历史）。
 *
 * <p>仅 {@code DISCHARGED}；同事务内嵌反结后拉回 {@code IN_PROGRESS}（不占床）。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院出院召回请求", requiredProperties = {"encounterId", "patientId"})
public class RecallInpatientDischargeRequest {

    /** 住院就诊主键；须为已清床离院 {@code DISCHARGED}。 */
    @NotNull(message = "就诊标识不能为空")
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者主键；须与就诊归属一致。 */
    @NotNull(message = "患者标识不能为空")
    @Schema(description = "患者 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /**
     * 可选：历史条选定的正向成功结算单 ID。
     *
     * <p>空则取末次正向 SUCCESS {@code INPATIENT_CLINIC} 单。</p>
     */
    @Schema(description = "正向成功结算单 ID；空则取末次")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentId;
}
