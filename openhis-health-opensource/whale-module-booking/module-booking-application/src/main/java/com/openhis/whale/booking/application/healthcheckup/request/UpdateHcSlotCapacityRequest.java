/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新时段容量请求。
 */
@Getter
@Setter
public class UpdateHcSlotCapacityRequest {

    /** 时段配额主键。 */
    @NotNull
    private Long daySlotId;

    /** 新容量（≥ 已约）。 */
    @NotNull
    private Integer newCapacity;
}
