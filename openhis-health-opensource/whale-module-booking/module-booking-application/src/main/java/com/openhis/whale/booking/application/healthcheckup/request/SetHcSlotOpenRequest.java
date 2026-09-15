/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 设置时段开放/关闭请求。
 */
@Getter
@Setter
public class SetHcSlotOpenRequest {

    /** 时段配额主键。 */
    @NotNull
    private Long daySlotId;

    /** 是否开放。 */
    @NotNull
    private Boolean openFlag;
}
