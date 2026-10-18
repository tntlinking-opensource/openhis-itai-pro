/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.dailysettlement.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院药房日结页初始化响应（IF-IP-PDC-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院药房日结页初始化响应")
public class LoadInpatientPharmacyDailySettlementPageInitResponse {

    /** 默认业务窗起（yyyy-MM-dd HH:mm:ss）。 */
    @Schema(description = "默认日结时间起")
    private String defaultDateFrom;

    /** 默认业务窗止（yyyy-MM-dd HH:mm:ss）。 */
    @Schema(description = "默认日结时间止")
    private String defaultDateTo;

    /** 可选药房下拉。 */
    @Schema(description = "药房下拉")
    private List<OptionDto> pharmacyOptions;

    /** 药师岗发药人下拉（全员多选）。 */
    @Schema(description = "发药人下拉")
    private List<OptionDto> practitionerOptions;

    /**
     * 重点药类型选项（基药/监控/集采/抗生素）；与 AS-RPT-011 同构，供品规销售排名筛选。
     */
    @Schema(description = "重点药类型下拉")
    private List<OptionDto> keyDrugTypeOptions;

    /**
     * 药品性质选项（麻醉/精一/精二）；与重点药类型分框，供品规销售排名筛选。
     */
    @Schema(description = "药品性质下拉")
    private List<OptionDto> medicinePropertyOptions;
}
