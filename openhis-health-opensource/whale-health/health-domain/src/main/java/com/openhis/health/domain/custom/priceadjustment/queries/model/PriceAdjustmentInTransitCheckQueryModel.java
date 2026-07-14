/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.priceadjustment.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 调价提交在途互斥校验结果（BR-PRC-SUB-003）。
 *
 * <p>应用层 / module.querysupport 组装；领域层不实现该预检。</p>
 */
@Getter
@Setter
public class PriceAdjustmentInTransitCheckQueryModel {

    /** 是否阻断提交；{@code true} 时须整单拒绝。 */
    private Boolean blocked;

    /** 原因码，与 {@link com.openhis.health.domain.share.constants.ErrorCodeConstants} 对齐。 */
    private String reasonCode;

    /** 面向用户/应用层展示的原因说明。 */
    private String reasonMessage;
}
