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

package com.openhis.health.application.integration.winning.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "卫宁目录网关连通性探测响应")
public class WinningCatalogPingResponse {

    @Schema(description = "是否已配置可用网关（非 Stub）")
    private Boolean configured;

    @Schema(description = "探测是否成功")
    private Boolean reachable;

    @Schema(description = "网关实现类说明")
    private String gatewayImplementation;

    @Schema(description = "说明信息")
    private String message;
}
