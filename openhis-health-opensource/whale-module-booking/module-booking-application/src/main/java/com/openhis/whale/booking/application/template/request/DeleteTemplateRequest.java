/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.template.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 删除模板。
 */
@Getter
@Setter
public class DeleteTemplateRequest {

    /** 模板主键。 */
    @NotNull
    private Long id;
}
