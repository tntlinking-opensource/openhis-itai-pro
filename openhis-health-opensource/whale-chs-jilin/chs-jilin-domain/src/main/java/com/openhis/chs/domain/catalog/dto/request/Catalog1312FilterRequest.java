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

import com.openhis.chs.domain.catalog.queries.ChsCatalogPageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 【1312】医保目录信息查询 data 节点筛选（与平台报文字段对齐）。
 *
 * <p>分页键 {@code pageNo}/{@code pageSize}/{@code keyword} 继承 {@link ChsCatalogPageQuery}（B.10.11）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "1312 目录筛选条件")
public class Catalog1312FilterRequest extends ChsCatalogPageQuery {

    /** 目录类别 list_type，如 101/102/201/301 */
    @Schema(description = "目录类别 list_type，如 101/102/201/301")
    private String listType;

    /** 医保目录编码 hilist_code */
    @Schema(description = "医保目录编码 hilist_code")
    private String hilistCode;

    /** 医保目录名称 hilist_name（精确/模糊由平台决定） */
    @Schema(description = "医保目录名称 hilist_name")
    private String hilistName;

    /** 拼音助记码 pinyin */
    @Schema(description = "拼音助记码 pinyin")
    private String pinyin;

    /**
     * 增量游标 updt_time（yyyy-MM-dd）。
     * 空则由服务端取本地镜像最大 updt_time，再无则使用默认起始日。
     */
    @Schema(description = "增量游标 updt_time（yyyy-MM-dd）")
    private String updtTime;

    /** 有效标志 vali_flag：1 有效 / 0 无效 */
    @Schema(description = "有效标志 vali_flag：1 有效 / 0 无效")
    private String valiFlag;

    /** 收费项目等级 chrgitm_lv：01 甲 / 02 乙 / 03 丙 */
    @Schema(description = "收费项目等级 chrgitm_lv")
    private String chrgitmLv;

    /** 限制使用标志 lmt_used_flag：1 是 / 0 否 */
    @Schema(description = "限制使用标志 lmt_used_flag")
    private String lmtUsedFlag;

    /** 查询时间点 query_date（yyyy-MM-dd） */
    @Schema(description = "查询时间点 query_date（yyyy-MM-dd）")
    private String queryDate;

    /** 目录开始日期 begndate（yyyy-MM-dd） */
    @Schema(description = "目录开始日期 begndate（yyyy-MM-dd）")
    private String begndate;
}
