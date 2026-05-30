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

package com.openhis.health.application.basic.devicecatalog.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新耗材目录请求：在创建字段基础上携带网关字符串主键。
 */
@Data
@EqualsAndHashCode(callSuper = true)
/**
 * UpdateDeviceCatalogRequest。
 */
public class UpdateDeviceCatalogRequest extends CreateDeviceCatalogRequest {
    /** 设备定义主键（网关字符串，解析见 {@link com.openhis.health.domain.share.utils.GatewayIdParseUtil}）。 */
    /**
     * id。
     */
    private String id;
    /** 编辑业务码（由上游业务判定并透传，示例：OK）。 */
    /**
     * editabilityCode。
     */
    private String editabilityCode;
}
