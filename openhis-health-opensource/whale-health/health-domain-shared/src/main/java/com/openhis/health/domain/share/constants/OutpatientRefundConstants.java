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

package com.openhis.health.domain.share.constants;

/**
 * 门诊退费应用编排共享常量（对齐 AS-OP-FIN-003 §5 EX-REF-*）。
 */
public interface OutpatientRefundConstants {

    /** 初始化失败。 */
    String MESSAGE_CODE_INIT_LOAD_FAILED = "EX-REF-101";

    /** 队列查询失败。 */
    String MESSAGE_CODE_QUEUE_QUERY_FAILED = "EX-REF-102";

    /** 就诊无效或无可执行退费明细。 */
    String MESSAGE_CODE_EXECUTION_DETAIL_INVALID = "EX-REF-103";

    /** 须先完成退药。 */
    String MESSAGE_CODE_MEDICATION_RETURN_PENDING = "EX-REF-201";

    /** 须先完成退耗材。 */
    String MESSAGE_CODE_DEVICE_RETURN_PENDING = "EX-REF-202";

    /** 目标账户不存在。 */
    String MESSAGE_CODE_ACCOUNT_NOT_FOUND = "EX-REF-401";

    /** 账户切换失败。 */
    String MESSAGE_CODE_ACCOUNT_SWITCH_FAILED = "EX-REF-402";

    /** 无半退保留行。 */
    String MESSAGE_CODE_NO_PRESERVED_LINES = "EX-REF-403";

    /** 支付不可退。 */
    String MESSAGE_CODE_PAYMENT_NOT_REFUNDABLE = "EX-REF-301";

    /** 收费行状态已变化。 */
    String MESSAGE_CODE_CHARGE_ITEM_STATUS_CHANGED = "EX-REF-302";

    /** 实退金额与应退不一致。 */
    String MESSAGE_CODE_REFUND_AMOUNT_MISMATCH = "EX-REF-303";

    /** 半退再结算失败。 */
    String MESSAGE_CODE_HALF_REFUND_SETTLE_FAILED = "EX-REF-304";

    /** 退费执行成功。 */
    String MESSAGE_CODE_REFUND_SUCCESS = "EX-REF-410";

    /** 账户切换成功。 */
    String MESSAGE_CODE_ACCOUNT_SWITCH_SUCCESS = "EX-REF-409";
}
