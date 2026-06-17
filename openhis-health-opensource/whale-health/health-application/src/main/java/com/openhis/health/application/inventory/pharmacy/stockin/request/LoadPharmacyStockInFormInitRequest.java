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

package com.openhis.health.application.inventory.pharmacy.stockin.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 药房入库页初始化请求（IF-PHA-IN-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
/** 药房入库页初始化请求 IF-PHA-IN-01。 */
@Schema(description = "药房入库页初始化请求 IF-PHA-IN-01")
public class LoadPharmacyStockInFormInitRequest {

    /** 兼容字段，服务端忽略（与进货/出库同源策略）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "机构主键，兼容字段，服务端忽略")
    private Long organizationId;
}
