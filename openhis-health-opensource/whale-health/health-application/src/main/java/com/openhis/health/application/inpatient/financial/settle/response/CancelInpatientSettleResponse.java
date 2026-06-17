/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.financial.settle.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 取消住院结算响应（CHS Wave 2 扩展）。
 */
@Data
@Accessors(chain = true)
/** 取消住院结算响应。 */
@Schema(description = "取消住院结算响应")
public class CancelInpatientSettleResponse {

    @Schema(description = "逆向退款支付单 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 逆向退款支付单 ID（业务字段）。 */
    private Long refundPaymentId;

    /** 是否已撤销医保正式结算。 */
    @Schema(description = "是否已撤销医保正式结算（2305）")
    /** 是否已撤销医保正式结算（2305）（业务字段）。 */
    private Boolean insuranceRevoked;

    /** 撤销后的医保结算 ID。 */
    @Schema(description = "撤销后的医保结算 ID")
    /** 撤销后的医保结算 ID（业务字段）。 */
    private String ybSetlId;
}
