/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.pharmacy.dispenserecord.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 门诊发退药记录页初始化响应（IF-OPR-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊发退药记录页初始化响应")
public class LoadOutpatientPharmacyDispenseRecordPageInitResponse {

    /** 默认发药日起（yyyy-MM-dd）。 */
    @Schema(description = "默认发药日起")
    private String defaultDispenseDateFrom;

    /** 默认发药日止（yyyy-MM-dd）。 */
    @Schema(description = "默认发药日止")
    private String defaultDispenseDateTo;

    /** 租户下全部启用药房。 */
    @Schema(description = "药房下拉")
    private List<OptionDto> pharmacyOptions;

    /** medication_category_code 术语选项。 */
    @Schema(description = "项目类型下拉")
    private List<OptionDto> medicationCategoryOptions;

    /** 发药人（药师岗）下拉。 */
    @Schema(description = "发药人下拉")
    private List<OptionDto> dispenserOptions;

    /** 记录类型：全部/发药/退药。 */
    @Schema(description = "记录类型下拉")
    private List<OptionDto> recordTypeOptions;
}
