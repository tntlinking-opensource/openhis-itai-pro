/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.frequency.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编辑频次请求：在 {@link CreateFrequencyRequest} 字段基础上携带技术主键。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateFrequencyRequest extends CreateFrequencyRequest {

    /** 频次聚合根主键。 */
    private Long id;
}
