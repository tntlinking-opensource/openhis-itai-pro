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
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 国家医保目录分页查询请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "国家医保目录分页查询请求")
public class QueryNationalCatalogRequest extends Catalog1312FilterRequest {

    /** 契约业务编码（fin_contract.bus_no），与 chs-contract 配置键一致 */
    @Schema(description = "契约业务编码 fin_contract.bus_no")
    private String contractBusNo;

    /** @deprecated 请使用 contractBusNo */
    @Deprecated
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "费用性质主键（已废弃，请用 contractBusNo）")
    private Long contractId;

    /** 本地无结果时是否按上方 1312 条件调平台补库 */
    @Schema(description = "本地无结果时是否调 1312 平台补库")
    private boolean queryPlatformIfMiss;

    /** 仅查指定来源，选码场景默认 1312 */
    @Schema(description = "来源接口号 source_infno，如 1312")
    private String sourceInfno;

    /** 按 hilist_code 去重（优先保留 1312 / updt_time 最新） */
    @Schema(description = "按 hilist_code 去重")
    private boolean dedupeByHilistCode;

    /** 批准文号模糊匹配 */
    @Schema(description = "批准文号 aprvno 模糊")
    private String aprvno;

    /** 规格模糊匹配 */
    @Schema(description = "规格 spec 模糊")
    private String spec;
}
