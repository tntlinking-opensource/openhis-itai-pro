/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.share;

/**
 * 挂号现场占号院点策略 {@code bkg_registration_policy.walk_in_slot_mode} 码值。
 *
 * <p>产品口径：手动挂号与用排班挂号解绑；号源类型下拉仅按医师可接诊配置（BR-REG-014），
 * 不因本策略与当日放号求交。占号仅当请求显式携带 {@code slotId}。</p>
 */
public final class RegistrationWalkInSlotModeConstants {

    /** 纯现场语义标识（历史兼容）；不驱动号源下拉求交。 */
    public static final String WALK_IN_NO_SLOT = "WALK_IN_NO_SLOT";

    /** 先约后挂（预约转挂号；P1 菜单未开放时由院点策略显式配置）。 */
    public static final String APPOINTMENT_FIRST = "APPOINTMENT_FIRST";

    /** 允许点选当日 Slot 占号（须传 slotId；非强制手动挂号自动占号）。 */
    public static final String WALK_IN_WITH_SLOT = "WALK_IN_WITH_SLOT";

    private RegistrationWalkInSlotModeConstants() {
    }
}
