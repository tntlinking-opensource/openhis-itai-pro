/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.noop.booking;

import com.zxplat.domain.infrastructure.integration.booking.inpatient.InpatientBedBookingQueryExtension;

/**
 * 未装配 booking Module 时的默认 {@link InpatientBedBookingQueryExtension}。
 *
 * <p>{@link InpatientBedBookingQueryExtension#isEnabled()} 继承接口默认 false；
 * 住院登记台查不到外部床位预约快照，走本地床位分配。</p>
 */
public final class NoOpInpatientBedBookingQueryExtension implements InpatientBedBookingQueryExtension {
}
