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
 * 取消住院结算请求（CHS Wave 2 扩展；AS-IP-FIN-003 非目标外挂）。
 */
@Data
@Accessors(chain = true)
/** 取消住院结算请求。 */
@Schema(description = "取消住院结算请求", requiredProperties = {"paymentId", "encounterId", "patientId"})
public class CancelInpatientSettleRequest {

    /** 待取消的成功支付单 ID。 */
    @NotNull(message = "支付单标识不能为空")
    @Schema(description = "成功支付单 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 成功支付单 ID（业务字段）。 */
    private Long paymentId;

    /** 住院就诊 ID。 */
    @NotNull(message = "就诊标识不能为空")
    /** 住院就诊 ID。 */
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 住院就诊 ID（业务字段）。 */
    private Long encounterId;

    /** 患者 ID。 */
    @NotNull(message = "患者标识不能为空")
    /** 患者 ID。 */
    @Schema(description = "患者 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者 ID（业务字段）。 */
    private Long patientId;
}
