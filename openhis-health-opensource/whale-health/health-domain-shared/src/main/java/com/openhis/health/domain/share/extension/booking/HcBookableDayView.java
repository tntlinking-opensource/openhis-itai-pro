/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.extension.booking;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/**
 * 可约日视图（跨模块 SPI）。
 */
@Getter
@Setter
public class HcBookableDayView {

    /** 计划日。 */
    private LocalDate planDate;

    /** 当日该池剩余合计。 */
    private Integer remaining;
}
