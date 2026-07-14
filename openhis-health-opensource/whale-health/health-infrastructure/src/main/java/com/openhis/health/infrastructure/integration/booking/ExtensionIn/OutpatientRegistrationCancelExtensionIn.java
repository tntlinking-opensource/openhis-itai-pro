/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.booking.ExtensionIn;

import lombok.Getter;
import lombok.Setter;

/**
 * 退号后通知 booking 释放预约的策略入参。
 *
 * <p>职责：携带退号原因与释放策略码，供 {@code OutpatientBookingRegistrationExtension#onRegistrationCancelled} 决定占号/预约回滚方式。</p>
 */
@Getter
@Setter
public class OutpatientRegistrationCancelExtensionIn {

    /** 退号原因文案（展示/审计用）。 */
    private String cancelReason;

    /** 预约释放策略码（booking 侧枚举 code，如回滚 slot、保留预约等）。 */
    private String policyCode;

    /** 操作人主键（释号审计；slot 写路径要求非空）。 */
    private Long operatorId;
}
