/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 删除团检批次请求（9.4）。
 */
@Getter
@Setter
public class DeleteGroupCheckupBatchRequest {

    /** 团检批次主键。 */
    @NotNull
    private Long id;
}
