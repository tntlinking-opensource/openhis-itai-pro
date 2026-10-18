/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.refund.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.outpatient.financial.refund.dto.OutpatientRefundPaymentGroupDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 就诊待执行退费明细响应（IF-REF-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "就诊待执行退费明细响应")
public class LoadOutpatientRefundExecutionDetailResponse {

    /** 就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键")
    private Long encounterId;

    /** 按原支付分组的拟退/保留明细。 */
    @Schema(description = "按原支付分组的明细")
    private List<OutpatientRefundPaymentGroupDto> paymentGroups;
}
