/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.prescriptionquery.response;

import java.util.List;

import com.zxplat.framework.core.application.dto.OptionDto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊处方查询页初始化响应（IF-OPQ-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊处方查询页初始化响应")
public class LoadOutpatientPharmacyPrescriptionQueryPageInitResponse {

    /** 默认开立日起（yyyy-MM-dd，本年首日）。 */
    @Schema(description = "默认开立日起")
    private String defaultOpenedDateFrom;

    /** 默认开立日止（yyyy-MM-dd，本年末日）。 */
    @Schema(description = "默认开立日止")
    private String defaultOpenedDateTo;

    /** 收费状态下拉（ChargeItemStatus 业务常用码）。 */
    @Schema(description = "收费状态下拉")
    private List<OptionDto> chargeStatusOptions;

    /** 发药状态下拉（DispenseStatus 业务常用码）。 */
    @Schema(description = "发药状态下拉")
    private List<OptionDto> dispenseStatusOptions;

    /** 术语 355 处方类别下拉。 */
    @Schema(description = "处方类别下拉")
    private List<OptionDto> rxTypeOptions;

    /** 医保类别下拉（门诊向 YbMedType）。 */
    @Schema(description = "医保类别下拉")
    private List<OptionDto> medTypeOptions;

    /** 费用性质分类下拉（自费 / 省医保 / 市医保三档）。 */
    @Schema(description = "费用性质分类下拉")
    private List<OptionDto> contractCategoryOptions;

    /** 含输液三态：全部/是/否。 */
    @Schema(description = "含输液筛选项")
    private List<OptionDto> infusionFilterOptions;

    /** 开单科室下拉（门诊启用科室；value=组织主键字符串）。 */
    @Schema(description = "开单科室下拉")
    private List<OptionDto> organizationOptions;

    /** 点评状态下拉（PENDING / REVIEWED）。 */
    @Schema(description = "点评状态下拉")
    private List<OptionDto> reviewStatusOptions;

    /** 是否合理下拉（合理 / 不合理；value 为字符串化布尔）。 */
    @Schema(description = "是否合理下拉")
    private List<OptionDto> reasonableOptions;

    /** 药品类型下拉（药典术语 medication_category_code）。 */
    @Schema(description = "药品类型下拉")
    private List<OptionDto> medicationCategoryOptions;
}
