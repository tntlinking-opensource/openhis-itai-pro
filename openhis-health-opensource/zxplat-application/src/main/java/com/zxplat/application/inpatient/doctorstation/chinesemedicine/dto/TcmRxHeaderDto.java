/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.chinesemedicine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * IF-DOR-34：中药处方方头展示 DTO（按方聚合）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "中药处方方头")
public class TcmRxHeaderDto {

    /** 前端方标识。 */
    @Schema(description = "前端方标识")
    private String prescriptionGroupKey;

    /** 处方号。 */
    @Schema(description = "处方号")
    private String prescriptionNo;

    /** 方头长临医嘱类型。 */
    @Schema(description = "方头长临医嘱类型")
    private String therapyEnum;

    /** 绑定的中医病证组号。 */
    @Schema(description = "绑定的中医病证组号")
    private String tcmSyndromeGroup;

    /** 方头绑定就诊诊断有序 ids 串（取组内首行；供前端多选回显）。 */
    @Schema(description = "方头绑定就诊诊断有序 ids 串")
    private String encounterDiagnosisIds;

    /**
     * 历史引用用病证组（含病名/证候目录主键）。
     * <p>IF-IP-TCM-39 历史 detail 附带；跨就诊复用时前端据此落到本次就诊。</p>
     */
    @Schema(description = "历史引用用病证组（含病名/证候目录主键）")
    private List<TcmDiagnosisGroupRowDto> reuseDiagnosisGroups = new ArrayList<>();

    /** 中药付数。 */
    @Schema(description = "中药付数")
    private BigDecimal chineseHerbsDoseQuantity;

    /** 代煎标志。 */
    @Schema(description = "代煎标志")
    private Boolean sufferingFlag;

    /** 用法编码。 */
    @Schema(description = "用法编码")
    private String methodCode;

    /** 用法展示名。 */
    @Schema(description = "用法展示名")
    private String methodName;

    /** 用药频次编码。 */
    @Schema(description = "用药频次编码")
    private String rateCode;

    /** 用药频次展示名。 */
    @Schema(description = "用药频次展示名")
    private String rateName;

    /** 用药天数。 */
    @Schema(description = "用药天数")
    private Integer days;

    /** 煎煮法编码。 */
    @Schema(description = "煎煮法编码")
    private String decoctionMethodCode;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /** 方状态。 */
    @Schema(description = "方状态")
    private String statusEnum;

    /** 方状态展示码。 */
    @Schema(description = "方状态展示码")
    private String displayStatusEnum;

    /** 方状态展示文案。 */
    @Schema(description = "方状态展示文案")
    private String displayStatusEnumText;

    /** 方内明细行。 */
    @Schema(description = "方内明细行")
    private List<TcmRxLineDto> lines = new ArrayList<>();
}
