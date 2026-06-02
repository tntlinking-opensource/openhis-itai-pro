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

package com.openhis.health.application.basic.chargetype.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编辑费用性质请求体：在 {@link CreateChargeTypeRequest} 字段基础上携带技术主键。
 */
@Data
@EqualsAndHashCode(callSuper = true)
/**
 * UpdateChargeTypeRequest。
 */
public class UpdateChargeTypeRequest extends CreateChargeTypeRequest {

    /** 费用性质（合约）主键。 */
    /**
     * id。
     */
    private Long id;
}
