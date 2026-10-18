/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 住院药房退药患者队列页签（AS-IP-PHA-003 IF-IPR-02 {@code queueTab}；对齐 BR-IPR-001）。
 */
public interface InpatientPharmacyReturnQueueTab {

    /** 待退 Tab：就诊下仍存在 {@code PENDING_REFUND} 发放行。 */
    String PENDING = "PENDING";

    /** 已退 Tab：仅有 {@code REFUNDED} 且无待退。 */
    String COMPLETED = "COMPLETED";
}
