/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 删除日时段配额请求。
 */
@Getter
@Setter
public class DeleteHcDaySlotRequest {

    /** 时段配额主键。 */
    @NotNull
    private Long daySlotId;
}
