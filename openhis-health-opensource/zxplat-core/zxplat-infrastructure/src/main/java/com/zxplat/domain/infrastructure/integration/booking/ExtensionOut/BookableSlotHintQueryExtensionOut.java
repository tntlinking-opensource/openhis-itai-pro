/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.booking.ExtensionOut;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 可约时段摘要快照（IF-REG-14）。
 *
 * <p>职责：挂号台选时段时展示剩余号源，不含患者/预约绑定信息。</p>
 */
@Getter
@Setter
public class BookableSlotHintQueryExtensionOut {

    /** 时段 slot 主键。 */
    private Long slotId;

    /** 时段开始时间（含日期）。 */
    private LocalDateTime slotStart;

    /** 时段结束时间。 */
    private LocalDateTime slotEnd;

    /** 剩余可约数量；为 0 时前端应禁用该时段。 */
    private Integer remainingCount;
}
