/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 住院结算工作台队列页签常量（AS-IP-FIN-003 IF-IP-STL-02）。
 */
public interface InpatientSettleQueueTab {

    /** 待出院结算（就诊 {@code PENDING}，护士已办理出院）。 */
    String PENDING_SETTLE = "PENDING_SETTLE";

    /** 已结算查阅（存在住院临床 SUCCESS/REFUNDED 结算流水；与当前在院/床位/召回态无关）。 */
    String SETTLED = "SETTLED";
}
