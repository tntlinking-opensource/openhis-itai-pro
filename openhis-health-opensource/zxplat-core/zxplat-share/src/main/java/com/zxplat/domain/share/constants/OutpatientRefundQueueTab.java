/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 门诊退费队列页签常量（IF-REF-02；对齐 AS BR-REF-002）。
 */
public interface OutpatientRefundQueueTab {

/** OutpatientRefundQueueTab 共享常量：ALL，值 {@code ALL}。 */
    String ALL = "ALL";

    /** 待退费标签（就诊仍存在 {@code REFUNDING} 拟退收费明细）。 */
    String PENDING = "PENDING";

    /**
     * 已退费标签（拟退明细已执行完毕，仅余 {@code REFUNDED} 过程终态；对外展示「已退费」）。
     *
     * <p>持久化码仍为 {@code SETTLED}，与门诊收费队列页签字面量复用，语义由 AS-OP-FIN-003 BR-REF-002 限定。</p>
     */
    String SETTLED = "SETTLED";
}
