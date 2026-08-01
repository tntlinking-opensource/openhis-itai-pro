/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.command;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 1317 从平台同步医药机构目录匹配（自动翻页拉取 + 落库 + 正向回写）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "1317 平台同步请求")
public class Refresh1317Command {

    /** 契约业务编码（fin_contract.bus_no） */
    @Schema(description = "契约业务编码 fin_contract.bus_no")
    private String contractBusNo;

    /** @deprecated 请使用 contractBusNo */
    @Deprecated
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "费用性质主键（已废弃，请传 contractBusNo）")
    private Long contractId;

    /** 目录类别（101/102/201/301）；空=平台不按类别过滤 */
    @Schema(description = "目录类别 list_type")
    private String listType;

    /** 有效标志；缺省由 Assembler 填 1 */
    @Schema(description = "有效标志 vali_flag")
    private String valiFlag;

    /** 查询时间点 */
    @Schema(description = "查询时间点 query_date")
    private String queryDate;

    /** 开始日期 */
    @Schema(description = "开始日期 begndate")
    private String begndate;

    /** 增量游标 updt_time */
    @Schema(description = "更新时间 updt_time")
    private String updtTime;

    /** 定点医药机构目录编号（精确） */
    @Schema(description = "院内目录编号 medins_list_codg")
    private String medinsListCodg;

    /** 定点医药机构目录名称 */
    @Schema(description = "院内目录名称 medins_list_name")
    private String medinsListName;

    /** 国家医保目录编码（精确） */
    @Schema(description = "医保目录编码 med_list_codg")
    private String medListCodg;

    /** 关键字（映射为 medins_list_name 模糊条件） */
    @Schema(description = "关键字")
    private String keyword;

    /** 单页大小；缺省 {@link com.openhis.chs.domain.share.constants.ChsCatalogConstants#DEFAULT_PLATFORM_PAGE_SIZE} */
    @Schema(description = "平台 page_size")
    private Integer platformPageSize;
}
