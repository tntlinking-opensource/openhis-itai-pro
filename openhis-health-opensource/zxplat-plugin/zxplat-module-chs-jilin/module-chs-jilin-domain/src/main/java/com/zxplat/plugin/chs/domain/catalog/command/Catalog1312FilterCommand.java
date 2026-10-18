/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.command;

import com.zxplat.plugin.chs.domain.catalog.queries.ChsCatalogPageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 【1312】医保目录信息查data 节点筛选（与平台报文字段对齐）。 *
 * <p>分页 {@code pageNo}/{@code pageSize}/{@code keyword} 继承 {@link ChsCatalogPageQuery}（B.10.11）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "1312 目录筛选条件")
public class Catalog1312FilterCommand extends ChsCatalogPageQuery {

    /** 目录类别 list_type，如 101/102/201/301 */
    @Schema(description = "目录类别 list_type，如 101/102/201/301")
    private String listType;

    /** 医保目录编码 hilist_code */
    @Schema(description = "医保目录编码 hilist_code")
    private String hilistCode;

    /** 医保目录名称 hilist_name（精模糊由平台决定） */
    @Schema(description = "医保目录名称 hilist_name")
    private String hilistName;

    /** 拼音助记pinyin */
    @Schema(description = "拼音助记pinyin")
    private String pinyin;

    /**
     * 增量游标 updt_time（yyyy-MM-dd）。     * 空则由服务端取本地镜像最updt_time，再无则使用默认起始日 */
    @Schema(description = "增量游标 updt_time（yyyy-MM-dd）")
    private String updtTime;

    /** 有效标志 vali_flag） 有效 / 0 无效 */
    @Schema(description = "有效标志 vali_flag） 有效 / 0 无效")
    private String valiFlag;

    /** 收费项目等级 chrgitm_lv）1 / 02 / 03  */
    @Schema(description = "收费项目等级 chrgitm_lv")
    private String chrgitmLv;

    /** 限制使用标志 lmt_used_flag） / 0  */
    @Schema(description = "限制使用标志 lmt_used_flag")
    private String lmtUsedFlag;

    /** 查询时间query_date（yyyy-MM-dd） */
    @Schema(description = "查询时间query_date（yyyy-MM-dd）")
    private String queryDate;

    /** 目录开始日begndate（yyyy-MM-dd） */
    @Schema(description = "目录开始日begndate（yyyy-MM-dd）")
    private String begndate;
}
