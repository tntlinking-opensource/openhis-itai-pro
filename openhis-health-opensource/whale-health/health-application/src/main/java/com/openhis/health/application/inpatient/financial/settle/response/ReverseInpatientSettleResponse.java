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
 * 住院反结响应（IF-IP-RVS-01，AS-IP-FIN-004）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院反结响应")
public class ReverseInpatientSettleResponse {

    /** 新建逆向反结支付单 ID（负向记录，收口后 REFUNDED）。 */
    @Schema(description = "逆向反结支付单 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long refundPaymentId;

    /** 反结是否成功；失败时抛异常，成功固定 true。 */
    @Schema(description = "反结是否成功")
    private Boolean succeeded;
}
