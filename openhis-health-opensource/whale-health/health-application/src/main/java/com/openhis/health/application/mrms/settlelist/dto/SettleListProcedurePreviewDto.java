/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.settlelist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 结算清单预览·手术行（国临 + 医保双码）。
 */
@Getter
@Setter
public class SettleListProcedurePreviewDto {

    /** 排序号。 */
    @Schema(description = "排序号")
    private Integer sortNo;

    /** 国临码（落库真源）。 */
    @Schema(description = "国临码")
    private String clinicCode;

    /** 医保码（目录对照；未对码为空）。 */
    @Schema(description = "医保码")
    private String ybNo;

    /** 上传出站码（仅医保；未对码为空）。 */
    @Schema(description = "上传出站码")
    private String outboundCode;

    /** 是否已对医保码。 */
    @Schema(description = "是否已对码")
    private Boolean mapped;

    /** 未对码提示。 */
    @Schema(description = "未对码提示")
    private String unmappedHint;

    /** DRG/扩展码预留列（本期无数据源时常空）。 */
    @Schema(description = "DRG扩展码预留")
    private String drgExtCode;

    /** 手术/操作名称。 */
    @Schema(description = "手术名称")
    private String oprnOprtName;

    /** 主手术标志。 */
    @Schema(description = "主手术标志")
    private String mainOprnFlag;

    /** 手术日期（首页仅存单日，对应老「手术开始时间」）。 */
    @Schema(description = "手术日期")
    private String oprnDate;

    /** 手术级别码。 */
    @Schema(description = "手术级别码")
    private String oprnLevelCode;

    /** 切口类别码。 */
    @Schema(description = "切口类别码")
    private String oprnIncisionCode;

    /** 愈合等级码。 */
    @Schema(description = "愈合等级码")
    private String healGradeCode;

    /** 麻醉方式码。 */
    @Schema(description = "麻醉方式码")
    private String anstWayCode;

    /** 术者姓名。 */
    @Schema(description = "术者姓名")
    private String operDrName;

    /** 一助姓名。 */
    @Schema(description = "一助姓名")
    private String asit1Name;

    /** 二助姓名。 */
    @Schema(description = "二助姓名")
    private String asit2Name;

    /** 配合护士姓名。 */
    @Schema(description = "配合护士姓名")
    private String nurseName;

    /** 麻醉医师姓名。 */
    @Schema(description = "麻醉医师姓名")
    private String anstDrName;
}
