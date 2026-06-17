/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
