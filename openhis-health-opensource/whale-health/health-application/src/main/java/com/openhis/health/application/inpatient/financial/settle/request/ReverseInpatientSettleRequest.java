/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.financial.settle.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院反结请求（IF-IP-RVS-01，AS-IP-FIN-004）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院反结请求", requiredProperties = {"paymentId", "encounterId", "patientId"})
public class ReverseInpatientSettleRequest {

    /** 用户于历史条选定的正向成功结算单 ID。 */
    @NotNull(message = "支付单标识不能为空")
    @Schema(description = "正向成功结算单 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentId;

    /** 住院就诊 ID，须与支付单头一致。 */
    @NotNull(message = "就诊标识不能为空")
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者 ID，须与支付单头一致。 */
    @NotNull(message = "患者标识不能为空")
    @Schema(description = "患者 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;
}
