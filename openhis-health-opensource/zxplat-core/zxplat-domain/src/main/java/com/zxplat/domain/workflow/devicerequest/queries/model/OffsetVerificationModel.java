/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicerequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 冲销校验结果模型。
 *
 * <p>权威口径：已冲销数量 = refundDeviceId=原单 且 quantity&lt;0 的历史行 quantity 绝对值之和。</p>
 */
@Getter
@Setter
public class OffsetVerificationModel {
    /** source请求主键。 */
    private Long sourceRequestId;
    /** source数量。 */
    private BigDecimal sourceQuantity;
    /** 冲抵数量。 */
    private BigDecimal offsetQuantity;
    /** remaining数量。 */
    private BigDecimal remainingQuantity;
}
