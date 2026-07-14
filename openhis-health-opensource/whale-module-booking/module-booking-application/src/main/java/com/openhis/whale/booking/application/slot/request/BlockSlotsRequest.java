/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.slot.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量封锁号源请求（3.3）。
 */
@Getter
@Setter
public class BlockSlotsRequest {

    /** 号源主键列表。 */
    @NotEmpty
    private List<Long> slotIds;

    /** 封锁原因（停诊/检修等）。 */
    private String reason;
}
