/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.noop.booking;

import com.zxplat.domain.infrastructure.integration.booking.outpatient.OutpatientBookingRegistrationExtension;

/**
 * 未装配 booking Module 时的默认 {@link OutpatientBookingRegistrationExtension}。
 *
 * <p>{@link OutpatientBookingRegistrationExtension#isEnabled()} 继承接口默认 false；
 * 挂号登记不校验外部预约约束，走现场挂号流程。</p>
 */
public final class NoOpOutpatientBookingRegistrationExtension implements OutpatientBookingRegistrationExtension {
}
