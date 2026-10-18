/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.command;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 1317 本地镜像分页查询。
 */
@Data
@Accessors(chain = true)
@Schema(description = "机构目录匹配本地镜像分页查询")
public class PageInstitutionCatalogMatchCommand {

    /** 契约业务编码（解析 contractId） */
    @Schema(description = "契约业务编码 fin_contract.bus_no")
    private String contractBusNo;

    /** 目录类别 */
    @Schema(description = "目录类别 list_type")
    private String listType;

    /** 有效标志 */
    @Schema(description = "有效标志 vali_flag")
    private String valiFlag;

    /**
     * 院内对照使用情况筛选。
     * <ul>
     *   <li>空 / ALL：不过滤</li>
     *   <li>ACTIVE：院内启用且存在</li>
     *   <li>UNUSED：院内停用/废弃，或院内无匹配（孤儿）</li>
     * </ul>
     */
    @Schema(description = "院内使用情况 ALL/ACTIVE/UNUSED")
    private String hospitalMatchFilter;

    /** 院内编码 / 名称 / 医保码关键字 */
    @Schema(description = "关键字")
    private String keyword;

    /** 页码，从 1 开始 */
    @Schema(description = "页码")
    private Integer pageNo;

    /** 每页条数 */
    @Schema(description = "每页条数")
    private Integer pageSize;
}
