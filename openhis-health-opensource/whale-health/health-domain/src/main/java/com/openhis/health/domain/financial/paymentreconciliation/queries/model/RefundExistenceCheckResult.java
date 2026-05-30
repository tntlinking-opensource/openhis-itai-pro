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

package com.openhis.health.domain.financial.paymentreconciliation.queries.model;

import lombok.Getter;
import lombok.Setter;
import com.openhis.health.domain.share.enums.PaymentStatus;

/**
 * 退费存在性检查结果：是否已存在关联退费单及该单状态。
 */
@Getter
@Setter
public class RefundExistenceCheckResult {
    /** 是否已存在退费记录。 */
    private Boolean exists;
    /** 已存在的退费支付单主键。 */
    private Long refundPaymentId;
    /** 退费支付单当前状态。 */
    private PaymentStatus refundStatusEnum;
}
