/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.priceadjustment.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 撤销调价草稿命令（DM-CUS-011 §5.1.6）。
 *
 * <p>仅 draft → deprecated；不涉及库存解锁。</p>
 */
@Getter
@Setter
public class WithdrawPriceAdjustmentDraftCommand {

    /** 调价单号 {@code bus_no}。 */
    private String busNo;

    /** 操作人（审计）。 */
    private Long operatorId;
}
