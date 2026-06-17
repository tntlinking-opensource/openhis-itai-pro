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
 * 住院药房退药患者队列页签（AS-IP-PHA-003 IF-IPR-02 {@code queueTab}；对齐 BR-IPR-001）。
 */
public interface InpatientPharmacyReturnQueueTab {

    /** 待退 Tab：就诊下仍存在 {@code PENDING_REFUND} 发放行。 */
    String PENDING = "PENDING";

    /** 已退 Tab：仅有 {@code REFUNDED} 且无待退。 */
    String COMPLETED = "COMPLETED";
}
