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
 * 门诊日结交账应用编排消息码与展示常量（对齐 AS-OP-FIN-004 §5）。
 */
public interface OutpatientDailySettlementConstants {

    /** 页面初始化失败（EX-OP-DCL-101）。 */
    String EX_OP_DCL_101 = "EX-OP-DCL-101";

    /** 日期或经办人参数非法（EX-OP-DCL-102）。 */
    String EX_OP_DCL_102 = "EX-OP-DCL-102";

    /** 日结查询失败（EX-OP-DCL-103）。 */
    String EX_OP_DCL_103 = "EX-OP-DCL-103";

    /** 无可交账流水（EX-OP-DCL-201）。 */
    String EX_OP_DCL_201 = "EX-OP-DCL-201";

    /** 并发交账冲突（EX-OP-DCL-301）。 */
    String EX_OP_DCL_301 = "EX-OP-DCL-301";

    /** 交账成功提示语义码（EX-OP-DCL-410，非异常）。 */
    String EX_OP_DCL_410 = "EX-OP-DCL-410";

    /** 费用性质筛选项：自费（BR-OP-DCL-009）。 */
    String EXPENSE_SELF_PAY = "selfPay";

    /** 费用性质筛选项：医保（BR-OP-DCL-009）。 */
    String EXPENSE_INSURANCE = "insurance";

    /** 费用性质展示：自费。 */
    String EXPENSE_SELF_PAY_LABEL = "自费";

    /** 费用性质展示：医保。 */
    String EXPENSE_INSURANCE_LABEL = "医保";

    /** 渠道汇总金额单位。 */
    String AMOUNT_UNIT_YUAN = "元";

    /** 优惠金额占位（本期固定 0.00）。 */
    String DISCOUNT_AMOUNT_ZERO = "0.00";

    /** 金额展示小数位。 */
    int AMOUNT_DISPLAY_SCALE = 2;

    /** 财务代交账经办人下拉单次加载上限。 */
    int CASHIER_OPTION_PAGE_SIZE = 500;

    /** 挂号费财务类别码（fin_type_code 特列，对齐 ter_concept 1011）。 */
    String REGISTRATION_FEE_FIN_TYPE_CODE = "1011";

    /** 体检财务类别码（fin_type_code，老日结单 peisCnt 锚点）。 */
    String PHYSICAL_EXAM_FIN_TYPE_CODE = "9999";

    /** payment 统计快照按 fin_type_code 汇总时的 method_code 标识。 */
    String PAYMENT_REC_STATIC_FIN_TYPE_METHOD = "fin_type_code";

    /** 自费合同 bus_no 占位（老日结单 0000 口径，院点可配置覆盖）。 */
    String SELF_PAY_CONTRACT_BUS_NO = "0000";

    /** 省医保合同 bus_no 占位（老日结单 229900 口径）。 */
    String PROVINCIAL_INSURANCE_CONTRACT_BUS_NO = "229900";

    /** 市医保合同 bus_no 占位（老日结单 220100 口径）。 */
    String MUNICIPAL_INSURANCE_CONTRACT_BUS_NO = "220100";

    /** 市医保（补充）合同 bus_no 占位（老日结单 220199 口径）。 */
    String MUNICIPAL_INSURANCE_SUPPLEMENT_CONTRACT_BUS_NO = "220199";
}
