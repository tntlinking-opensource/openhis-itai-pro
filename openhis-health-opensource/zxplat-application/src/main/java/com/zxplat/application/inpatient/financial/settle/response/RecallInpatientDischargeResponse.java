/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.settle.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院出院召回响应（AS-IP-FIN-004 召回写）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院出院召回响应")
public class RecallInpatientDischargeResponse {

    /** 就诊主键。 */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean succeeded;

    /** 召回后就诊状态 code（{@code IN_PROGRESS}）。 */
    @Schema(description = "召回后就诊状态 code")
    private String targetStatusEnum;

    /**
     * 是否须二次分床。
     *
     * <p>召回未占床轨恒为 {@code false}。</p>
     */
    @Schema(description = "是否须二次分床")
    private Boolean requiresBedAssignment;

    /** 逆向反结支付单 ID（内嵌反结产物）。 */
    @Schema(description = "逆向反结支付单 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long refundPaymentId;
}
