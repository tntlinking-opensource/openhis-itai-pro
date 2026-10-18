/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.noop.booking;

import com.zxplat.domain.infrastructure.integration.booking.inpatient.InpatientBedBookingConsumeExtension;

/**
 * 未装配 booking Module 时的默认 {@link InpatientBedBookingConsumeExtension}。
 *
 * <p>{@link InpatientBedBookingConsumeExtension#isEnabled()} 继承接口默认 false；
 * 住院床位占用/释放走本地流程，不调用外部预约系统。</p>
 */
public final class NoOpInpatientBedBookingConsumeExtension implements InpatientBedBookingConsumeExtension {
}
