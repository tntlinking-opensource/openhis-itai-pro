/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.noop.booking;

import com.zxplat.domain.infrastructure.integration.booking.outpatient.OutpatientBookingQueryExtension;

/**
 * 未装配 booking Module 时的默认 {@link OutpatientBookingQueryExtension}。
 *
 * <p>{@link OutpatientBookingQueryExtension#isEnabled()} 继承接口默认 false；
 * 挂号台查不到外部预约快照，号源与排班仅来自 HIS 本地。</p>
 */
public final class NoOpOutpatientBookingQueryExtension implements OutpatientBookingQueryExtension {
}
