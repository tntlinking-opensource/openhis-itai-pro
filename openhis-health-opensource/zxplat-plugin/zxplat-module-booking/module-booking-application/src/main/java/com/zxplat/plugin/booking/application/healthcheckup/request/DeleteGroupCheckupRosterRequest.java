/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 删除团检名单请求（9.5）。
 */
@Getter
@Setter
public class DeleteGroupCheckupRosterRequest {

    /** 团检名单主键。 */
    @NotNull
    private Long id;
}
