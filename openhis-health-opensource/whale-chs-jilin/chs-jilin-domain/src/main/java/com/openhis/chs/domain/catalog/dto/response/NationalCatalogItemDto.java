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

package com.openhis.chs.domain.catalog.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 国家医保目录镜像行 DTO。
 */
@Data
@Accessors(chain = true)
@Schema(description = "国家医保目录镜像行")
public class NationalCatalogItemDto {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "镜像行主键")
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "费用性质/契约主键")
    private Long contractId;

    @Schema(description = "目录类别 list_type")
    private String listType;

    @Schema(description = "医保目录编码 hilist_code")
    private String hilistCode;

    @Schema(description = "医保目录名称 hilist_name")
    private String hilistName;

    @Schema(description = "收费项目等级 chrgitm_lv")
    private String chrgitmLv;

    @Schema(description = "限制使用标志 lmt_used_flag")
    private String lmtUsedFlag;

    @Schema(description = "目录开始日期 begndate")
    private LocalDate begndate;

    @Schema(description = "目录结束日期 enddate")
    private LocalDate enddate;

    @Schema(description = "有效标志 vali_flag")
    private String valiFlag;

    @Schema(description = "拼音助记码 pinyin")
    private String pinyin;

    @Schema(description = "规格 spec")
    private String spec;

    @Schema(description = "剂型 dosform")
    private String dosform;

    @Schema(description = "批准文号 aprvno")
    private String aprvno;

    @Schema(description = "生产企业名称 prodentp_name（1301）")
    private String prodentpName;

    @Schema(description = "来源接口号 source_infno")
    private String sourceInfno;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "平台 updt_time")
    private LocalDateTime updtTime;
}
