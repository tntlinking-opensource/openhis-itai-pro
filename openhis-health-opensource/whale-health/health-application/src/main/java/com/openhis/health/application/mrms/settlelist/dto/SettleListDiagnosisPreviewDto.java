/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.settlelist.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 结算清单预览·诊断行（列对齐老 drgdiagnoseGrid）。
 *
 * <p>西医：国临列=落库/反查国临，医保列=目录对照；
 * 中医：国临列空，医保列=落库中医目录码/名；上传出站用该码。</p>
 */
@Getter
@Setter
public class SettleListDiagnosisPreviewDto {

    /** 诊断类型码（HomepageDiagType）。 */
    @Schema(description = "诊断类型码")
    private String diagTypeCode;

    /** 诊断类型展示名。 */
    @Schema(description = "诊断类型展示")
    private String diagTypeLabel;

    /**
     * 中西医维度码（{@code WESTERN}/{@code TCM}），由目录来源推导。
     */
    @Schema(description = "中西医维度码")
    private String diagnosisKind;

    /** 中西医维度展示（西医/中医）。 */
    @Schema(description = "中西医维度展示")
    private String diagnosisKindLabel;

    /** 排序号。 */
    @Schema(description = "排序号")
    private Integer sortNo;

    /**
     * 国临码（西医落库/反查真源）；中医预览恒空。
     */
    @Schema(description = "国临码；中医为空")
    private String clinicCode;

    /**
     * 国临目录（clinical_v2）主键；西医未对码时前端「对码」写回用。
     * 中医恒空。雪花 Long 序列化为字符串，避免前端精度丢失。
     */
    @Schema(description = "国临目录主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long clinicalDefinitionId;

    /**
     * 医保码：西医为目录对照；中医为落库中医目录码。
     */
    @Schema(description = "医保码")
    private String ybNo;

    /**
     * 医保名称：西医为目录对照；中医为落库中医目录名。
     */
    @Schema(description = "医保名称")
    private String ybName;

    /**
     * 上传出站码：西医为医保对照码；中医为落库中医目录码。
     */
    @Schema(description = "上传出站码")
    private String outboundCode;

    /**
     * 是否已对医保码。
     * <ul>
     *   <li>西医：true/false；false 时前端可点「对码」</li>
     *   <li>中医：恒 null（不对国临→医保，不标未对码）</li>
     * </ul>
     */
    @Schema(description = "是否已对码；中医为 null")
    private Boolean mapped;

    /** 未对码提示（西医不可上传）；中医为 null。 */
    @Schema(description = "未对码提示")
    private String unmappedHint;

    /**
     * 国临诊断名称（西医）；中医预览恒空（名称在 {@link #ybName}）。
     */
    @Schema(description = "国临诊断名称；中医为空")
    private String diagName;

    /** 入院病情码。 */
    @Schema(description = "入院病情码")
    private String admCondCode;

    /**
     * 诊断转归/病情码（对齐老系统 outIllStatus：1治愈 2好转 3未愈 4死亡 9其他）。
     */
    @Schema(description = "病情码")
    private String outIllStatusCode;

    /** 确诊时间（结算清单；手填，对齐老 confirmDate）。 */
    @Schema(description = "确诊时间")
    private LocalDateTime confirmTime;

    /**
     * 是否上传医保（对齐首页 {@code need_yb_up}：1 是 / 0 否）。
     * <p>出院行缺省按 1；{@code 0} 时 4101A/4401 出站跳过该诊断。</p>
     */
    @Schema(description = "是否上传：1是 0否")
    private String needYbUp;

    /** 说明（手填）。 */
    @Schema(description = "说明")
    private String diagRemark;

    /** 主诊标志。 */
    @Schema(description = "主诊标志")
    private String maindiagFlag;
}
