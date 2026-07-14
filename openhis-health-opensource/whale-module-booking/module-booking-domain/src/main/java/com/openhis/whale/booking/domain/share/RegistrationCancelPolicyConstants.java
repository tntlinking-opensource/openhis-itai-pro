/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.share;

/**
 * 挂号退号时预约/号源处置策略（{@code bkg_registration_policy.on_cancel_policy}）。
 */
public final class RegistrationCancelPolicyConstants {

    /** 释放号源占用（默认）；不强制取消预约行，但须回补 bookedCount。 */
    public static final String RELEASE_SLOT_ONLY = "RELEASE_SLOT_ONLY";

    /** 保留号源占用（仅解绑就诊与预约关联）。 */
    public static final String KEEP_SLOT = "KEEP_SLOT";

    /** 取消预约并释放号源占用。 */
    public static final String CANCEL_APPOINTMENT = "CANCEL_APPOINTMENT";

    private RegistrationCancelPolicyConstants() {
    }
}
