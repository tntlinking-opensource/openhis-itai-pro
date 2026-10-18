/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.result;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 国家医保目录镜像行DTO */
@Data
@Accessors(chain = true)
@Schema(description = "国家医保目录镜像行")
public class NationalCatalogItemResult {

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

    @Schema(description = "目录开始日begndate")
    private LocalDate begndate;

    @Schema(description = "目录结束日期 enddate")
    private LocalDate enddate;

    @Schema(description = "有效标志 vali_flag")
    private String valiFlag;

    @Schema(description = "拼音助记pinyin")
    private String pinyin;

    @Schema(description = "规格 spec")
    private String spec;

    @Schema(description = "剂型 dosform")
    private String dosform;

    @Schema(description = "批准文号 aprvno")
    private String aprvno;

    @Schema(description = "生产企业名称 prodentp_name）301）")
    private String prodentpName;

    @Schema(description = "来源接口号source_infno")
    private String sourceInfno;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "平台 updt_time")
    private LocalDateTime updtTime;

    @Schema(description = "选码是否可选（镜像 ⋈ overlay 合成）")
    private Boolean isSelectable;

    @Schema(description = "不可选原因（平台停用 / 局文停用等）")
    private String unselectableReason;

    @Schema(description = "局文可用标志 notice_avali_flag")
    private String noticeAvaliFlag;

    @Schema(description = "局文终止日 notice_end_date")
    private LocalDate noticeEndDate;

    @Schema(description = "合成限价上限 hilist_pric_uplmt_amt（来自 overlay patch）")
    private String priceUplmtAmt;

    @Schema(description = "1301 最小包装数量 min_pac_cnt")
    private String minPacCnt;

    @Schema(description = "1301 最小包装单位 min_pacunt")
    private String minPacunt;

    @Schema(description = "1301 最小制剂单位 min_prepunt")
    private String minPrepunt;

    @Schema(description = "1301 最小计价单位 min_prcunt（限价口径）")
    private String minPrcunt;
}
