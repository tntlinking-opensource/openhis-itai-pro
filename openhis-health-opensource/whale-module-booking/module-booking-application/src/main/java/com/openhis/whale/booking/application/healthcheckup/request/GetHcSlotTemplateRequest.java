/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 加载体检号源模板请求。
 */
@Getter
@Setter
public class GetHcSlotTemplateRequest {

    /** 机构主键。 */
    @NotNull
    private Long organizationId;
}
