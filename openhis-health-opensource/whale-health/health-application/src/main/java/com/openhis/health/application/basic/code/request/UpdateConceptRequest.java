/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.code.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新概念请求：在 {@link CreateConceptRequest} 字段基础上增加主键。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateConceptRequest extends CreateConceptRequest {

    /** 概念主键。 */
    @NotNull(message = "编码ID不能为空")
    private Long id;
}
