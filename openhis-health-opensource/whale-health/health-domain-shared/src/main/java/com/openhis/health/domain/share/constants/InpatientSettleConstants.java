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
 * 住院结算应用编排消息码与呈现常量（对齐 AS-IP-FIN-003 §5）。
 */
public interface InpatientSettleConstants {

    /** 未选费用或金额非法（EX-IP-STL-001）。 */
    String EX_IP_STL_001 = "EX-IP-STL-001";

    /** 就诊不存在或非住院（EX-IP-STL-102）。 */
    String EX_IP_STL_102 = "EX-IP-STL-102";

    /** 个人现金账户不存在（EX-IP-STL-103）。 */
    String EX_IP_STL_103 = "EX-IP-STL-103";

    /** 费用展示数据不完整（EX-IP-STL-104）。 */
    String EX_IP_STL_104 = "EX-IP-STL-104";

    /** 存在未完成的结算草稿（EX-IP-STL-105）。 */
    String EX_IP_STL_105 = "EX-IP-STL-105";

    /** 草稿失效或渠道金额与应收不符（EX-IP-STL-106）。 */
    String EX_IP_STL_106 = "EX-IP-STL-106";

    /** 出院全结未包含全部待结算项（EX-IP-STL-107）。 */
    String EX_IP_STL_107 = "EX-IP-STL-107";

    /** 就诊未处于待结算态却按出院全结提交（EX-IP-STL-108）。 */
    String EX_IP_STL_108 = "EX-IP-STL-108";

    /** 支付结算失败（EX-IP-STL-109）。 */
    String EX_IP_STL_109 = "EX-IP-STL-109";

    /** 已结患者无结算记录（EX-IP-STL-110）。 */
    String EX_IP_STL_110 = "EX-IP-STL-110";

    /** 队列或初始化失败（EX-IP-STL-101）。 */
    String EX_IP_STL_101 = "EX-IP-STL-101";

    /** 费用呈现：明细清单（可勾选）。 */
    String PRESENTATION_MODE_DETAIL = "DETAIL";

    /** 费用呈现：大类汇总。 */
    String PRESENTATION_MODE_SUMMARY = "SUMMARY";

    /** 住院结算发票类型码（草稿）。 */
    String INVOICE_TYPE_INPATIENT = "INPATIENT";

    /** 正式结算成功语义码。 */
    String MESSAGE_CODE_SETTLE_SUCCESS = "EX-IP-STL-SUCCESS";

    /** 结算金额与明细合计比较小数位。 */
    int SETTLE_AMOUNT_COMPARE_SCALE = 2;
}
