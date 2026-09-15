/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.extension.booking;

import java.time.LocalTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 可约时段视图（跨模块 SPI）。
 */
@Getter
@Setter
public class HcBookableSlotView {

    /** 时段配额主键。 */
    private Long daySlotId;

    /** 开始时刻。 */
    private LocalTime startTime;

    /** 结束时刻。 */
    private LocalTime endTime;

    /** 合同类型编码。 */
    private String contractTypeCode;

    /** 校区编码（可空）。 */
    private String campusCode;

    /** 容量。 */
    private Integer capacity;

    /** 已约。 */
    private Integer bookedCount;

    /** 剩余。 */
    private Integer remaining;
}
