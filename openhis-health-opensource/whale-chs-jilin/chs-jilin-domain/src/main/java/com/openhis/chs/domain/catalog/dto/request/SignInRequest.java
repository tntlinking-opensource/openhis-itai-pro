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

package com.openhis.chs.domain.catalog.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 9001 签到请求（mac/ip 与 OpenHIS 一致，来自 HeaSecReadInfo 插件）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医保 9001 签到请求")
public class SignInRequest {

    /** 契约业务编码（fin_contract.bus_no） */
    @Schema(description = "契约业务编码 fin_contract.bus_no")
    private String contractBusNo;

    /** @deprecated 请使用 contractBusNo */
    @Deprecated
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "费用性质主键（已废弃，请用 contractBusNo）")
    private Long contractId;

    /** HeaSecReadInfo GetClientID 返回的 MAC 加密串 */
    @Schema(description = "HeaSecReadInfo 返回的 MAC 加密串")
    private String mac;

    /** 签到 IP */
    @Schema(description = "签到 IP")
    private String ip;
}
