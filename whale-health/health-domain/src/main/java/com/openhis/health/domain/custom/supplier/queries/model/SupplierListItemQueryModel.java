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

package com.openhis.health.domain.custom.supplier.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 供应商列表项查询模型。
 */
@Getter
@Setter
public class SupplierListItemQueryModel {
    /**
     * 供应商标识。
     */
    private Long id;
    /**
     * 供应商业务编码。
     */
    private String busNo;
    /**
     * 供应商名称。
     */
    private String name;
    /**
     * 供应商类型枚举值。
     */
    private String typeEnum;
    /**
     * 状态枚举值。
     */
    private String statusEnum;
    /**
     * 拼音码（可空）。
     */
    private String pyStr;
}

