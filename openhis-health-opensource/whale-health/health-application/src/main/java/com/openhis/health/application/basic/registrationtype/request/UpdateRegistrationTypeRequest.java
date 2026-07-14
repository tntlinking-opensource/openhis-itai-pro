/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.registrationtype.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编辑挂号类型请求：在 {@link CreateRegistrationTypeRequest} 字段上增加医疗服务主键。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateRegistrationTypeRequest extends CreateRegistrationTypeRequest {
    /** 医疗服务（挂号类型）主键。 */
    private Long id;
}
