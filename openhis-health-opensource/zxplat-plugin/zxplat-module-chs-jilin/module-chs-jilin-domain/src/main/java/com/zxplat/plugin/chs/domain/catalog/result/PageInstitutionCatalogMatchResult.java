/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 1317 本地镜像分页结果。
 */
@Data
@Accessors(chain = true)
@Schema(description = "机构目录匹配本地镜像分页结果")
public class PageInstitutionCatalogMatchResult {

    /** 当前页行 */
    @Schema(description = "行列表")
    private List<InstitutionCatalogMatchRow> rows = new ArrayList<>();

    /** 总行数 */
    @Schema(description = "总行数")
    private long total;

    /** 页码 */
    @Schema(description = "页码")
    private int pageNo;

    /** 每页条数 */
    @Schema(description = "每页条数")
    private int pageSize;

    /**
     * 镜像单行展示。
     */
    @Data
    @Accessors(chain = true)
    @Schema(description = "机构目录匹配行")
    public static class InstitutionCatalogMatchRow {

        /** 主键 */
        @Schema(description = "主键")
        private Long id;
        /** 定点机构编号 */
        @Schema(description = "定点机构编号")
        private String fixmedinsCode;
        /** 院内目录编号 */
        @Schema(description = "院内目录编号")
        private String medinsListCodg;
        /** 院内目录名称 */
        @Schema(description = "院内目录名称")
        private String medinsListName;
        /** 目录类别 */
        @Schema(description = "目录类别")
        private String listType;
        /** 医保目录编码 */
        @Schema(description = "医保目录编码")
        private String medListCodg;
        /** 有效标志 */
        @Schema(description = "有效标志")
        private String valiFlag;
        /** 开始日期 */
        @Schema(description = "开始日期")
        private LocalDate begndate;
        /** 结束日期 */
        @Schema(description = "结束日期")
        private LocalDate enddate;
        /** 批准文号 */
        @Schema(description = "批准文号")
        private String aprvno;
        /** 规格 */
        @Schema(description = "规格")
        private String spec;
        /** 剂型 */
        @Schema(description = "剂型")
        private String dosform;
        /** 备注 */
        @Schema(description = "备注")
        private String memo;
        /** 平台更新时间 */
        @Schema(description = "平台更新时间")
        private LocalDateTime updtTime;

        /**
         * 院内匹配状态：ACTIVE / INACTIVE / MISSING。
         * <p>ACTIVE=院内启用；INACTIVE=院内停用/废弃；MISSING=院内无此 bus_no。</p>
         */
        @Schema(description = "院内匹配状态 ACTIVE/INACTIVE/MISSING")
        private String hospitalMatchStatus;

        /** 院内产品表名（命中时） */
        @Schema(description = "院内产品表")
        private String hospitalProductTable;

        /** 院内产品主键（命中时） */
        @Schema(description = "院内产品主键")
        private Long hospitalProductId;

        /** 院内 status_enum（命中时） */
        @Schema(description = "院内发布状态")
        private String hospitalStatusEnum;
    }
}
