/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 住院预交金应用编排消息码（对齐 AS-IP-FIN-002 §5）。
 */
public interface InpatientPrepayConstants {

    /** 未选患者或金额≤0（EX-IP-PREP-001）。 */
    String EX_IP_PREP_001 = "EX-IP-PREP-001";

    /** 个人现金账户不存在（EX-IP-PREP-002）。 */
    String EX_IP_PREP_002 = "EX-IP-PREP-002";

    /** 就诊状态不允许收退预交金（EX-IP-PREP-003）。 */
    String EX_IP_PREP_003 = "EX-IP-PREP-003";

    /** 收取或结算失败（EX-IP-PREP-004）。 */
    String EX_IP_PREP_004 = "EX-IP-PREP-004";

    /** 退费金额大于当前账户余额（EX-IP-PREP-005）。 */
    String EX_IP_PREP_005 = "EX-IP-PREP-005";

    /** 退费后余额低于账户下限（EX-IP-PREP-006）。 */
    String EX_IP_PREP_006 = "EX-IP-PREP-006";

    /** 收取时未填支付明细（EX-IP-PREP-008）。 */
    String EX_IP_PREP_008 = "EX-IP-PREP-008";

    /** 支付明细合计与主金额不一致（EX-IP-PREP-007）。 */
    String EX_IP_PREP_007 = "EX-IP-PREP-007";

    /** 队列查询失败（EX-IP-PREP-101）。 */
    String EX_IP_PREP_101 = "EX-IP-PREP-101";

    /** 就诊非住院或不存在（EX-IP-PREP-102）。 */
    String EX_IP_PREP_102 = "EX-IP-PREP-102";

    /** 流水业务类型展示：收取预交金（IF-IP-PREP-02 bizTypeText）。 */
    String BIZ_TYPE_TEXT_COLLECT = "收预交金";

    /** 流水业务类型展示：退还预交金（IF-IP-PREP-02 bizTypeText）。 */
    String BIZ_TYPE_TEXT_REFUND = "退预交金";

    /**
     * 预交金主金额与明细合计比较小数位（AS-SDD §2.6，与收银台展示一致）。
     */
    int PREPAY_AMOUNT_COMPARE_SCALE = 2;
}
