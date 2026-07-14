/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 住院结算应用编排消息码与呈现常量（对齐 AS-IP-FIN-003 §5、AS-IP-FIN-004 §5）。
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

    /** 待出院（ON_HOLD）禁止任何结算，须先护士办理出院（EX-IP-STL-111）。 */
    String EX_IP_STL_111 = "EX-IP-STL-111";

    /** 本期不支持中途结算（EX-IP-STL-112）。 */
    String EX_IP_STL_112 = "EX-IP-STL-112";

    /** 支付结算失败（EX-IP-STL-109）。 */
    String EX_IP_STL_109 = "EX-IP-STL-109";

    /** 已结患者无结算记录（EX-IP-STL-110）。 */
    String EX_IP_STL_110 = "EX-IP-STL-110";

    /** 原单非成功态、非住院结算单、三主键不一致或已反结（EX-IP-RVS-001）。 */
    String EX_IP_RVS_001 = "EX-IP-RVS-001";

    /** 本批费用非已结算态（EX-IP-RVS-002）。 */
    String EX_IP_RVS_002 = "EX-IP-RVS-002";

    /** 就诊已清床离院，须护士站召回内嵌反结（EX-IP-RVS-003）。 */
    String EX_IP_RVS_003 = "EX-IP-RVS-003";

    /** 须撤出院嘱但未找到可退回医嘱（EX-IP-RVS-004）。 */
    String EX_IP_RVS_004 = "EX-IP-RVS-004";

    /** 反结持久化失败（EX-IP-RVS-005）。 */
    String EX_IP_RVS_005 = "EX-IP-RVS-005";

    /** 队列或初始化失败（EX-IP-STL-101）。 */
    String EX_IP_STL_101 = "EX-IP-STL-101";

    /** 费用呈现：明细清单（可勾选）。 */
    String PRESENTATION_MODE_DETAIL = "DETAIL";

    /** 费用呈现：大类汇总。 */
    String PRESENTATION_MODE_SUMMARY = "SUMMARY";

    /** 住院结算发票类型码（草稿）。 */
    String INVOICE_TYPE_INPATIENT = "INPATIENT";

    /** 住院反结时票据作废/冲红原因。 */
    String INVOICE_CANCEL_REASON_INPATIENT_SETTLE = "住院反结";

    /** 已结算历史条：正向结单。 */
    String SETTLED_PAYMENT_RECORD_TYPE_FORWARD = "FORWARD";

    /** 已结算历史条：逆向反结单。 */
    String SETTLED_PAYMENT_RECORD_TYPE_REVERSE = "REVERSE";

    /** 不可反结：该正向单已存在逆向关联。 */
    String REVERSE_DISABLED_REASON_ALREADY_REVERSED = "已反结";

    /** 不可反结：当前行为逆向记录。 */
    String REVERSE_DISABLED_REASON_REVERSE_RECORD = "逆向记录不可反结";

    /** 不可反结：就诊已清床离院。 */
    String REVERSE_DISABLED_REASON_DISCHARGED = "已清床请至护士站召回";

    /** 不可反结：原单非成功态。 */
    String REVERSE_DISABLED_REASON_NOT_SUCCESS = "非成功结算单";

    /** 撤销出院嘱退回草稿原因（BR-IP-RVS-007）。 */
    String DISCHARGE_SERVICE_REJECT_REASON_INPATIENT_REVERSE = "住院反结";

    /** 正式结算成功语义码。 */
    String MESSAGE_CODE_SETTLE_SUCCESS = "EX-IP-STL-SUCCESS";

    /** 结算金额与明细合计比较小数位。 */
    int SETTLE_AMOUNT_COMPARE_SCALE = 2;

/** InpatientSettle 共享常量：mode discharge full，值 {@code DISCHARGE_FULL}。 */
    String SETTLE_MODE_DISCHARGE_FULL = "DISCHARGE_FULL";

/** InpatientSettle 共享常量：mode mid，值 {@code MID_SETTLE}。 */
    String SETTLE_MODE_MID = "MID_SETTLE";

    /** 医保：PENDING 部分勾选未声明中途（INS-IP-001）。 */
    String INS_IP_001 = "INS-IP-001";

    /** 医保：显式中途但已全选 PENDING（INS-IP-016）。 */
    String INS_IP_016 = "INS-IP-016";
}
