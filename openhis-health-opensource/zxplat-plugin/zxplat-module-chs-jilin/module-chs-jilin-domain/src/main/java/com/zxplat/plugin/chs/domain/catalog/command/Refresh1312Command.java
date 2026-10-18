/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.command;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 1312 实时刷新国家目录请求 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "1312 实时刷新国家目录请求")
public class Refresh1312Command extends Catalog1312FilterCommand {

    /** 契约业务编码（fin_contract.bus_no） */
    @Schema(description = "契约业务编码 fin_contract.bus_no")
    private String contractBusNo;

    /** @deprecated 请使用 contractBusNo */
    @Deprecated
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "费用性质主键（已废弃，请求 contractBusNo）")
    private Long contractId;

    /** 1312 平台 page_num（非 HTTP pageNo） */
    @Schema(description = "1312 平台 page_num")
    private Integer platformPageNum;

    /** 1312 平台 page_size（非 HTTP pageSize） */
    @Schema(description = "1312 平台 page_size")
    private Integer platformPageSize;
}
