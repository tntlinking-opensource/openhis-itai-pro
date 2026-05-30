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

package com.openhis.health.domain.workflow.devicerequest.queries.model;

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

