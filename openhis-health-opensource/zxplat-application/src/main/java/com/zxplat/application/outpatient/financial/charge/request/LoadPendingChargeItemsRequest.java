/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.charge.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 加载就诊待收费明细请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "加载就诊待收费明细请求")
public class LoadPendingChargeItemsRequest {

    /** 就诊主键。 */
    private Long encounterId;

    /**
     * 可选：指定成功支付流水（队列行 paymentId）；空则取就诊最近成功支付。
     */
    private Long paymentId;

    /** 是否加载定价扩展字段（成本/折扣等）。 */
    private Boolean includePriceExtension;
}
