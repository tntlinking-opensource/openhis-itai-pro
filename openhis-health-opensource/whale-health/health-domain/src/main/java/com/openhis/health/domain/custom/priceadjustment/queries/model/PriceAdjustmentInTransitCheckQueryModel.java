/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
