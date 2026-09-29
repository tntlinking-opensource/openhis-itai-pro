/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.keymedicationusage.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 重点药品使用统计页初始化响应（IF-RPT-KDU-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "重点药品使用统计页初始化响应")
public class LoadKeyMedicationUsageReportPageInitResponse {

    /**
     * 默认业务日起（当日）。
     */
    @Schema(description = "默认业务日起")
    private String defaultDateFrom;

    /**
     * 默认业务日止（当日）。
     */
    @Schema(description = "默认业务日止")
    private String defaultDateTo;

    /**
     * 就诊类型选项（门诊/住院）。
     */
    @Schema(description = "就诊类型选项")
    private List<OptionDto> encounterClassOptions;

    /**
     * 重点药类型选项（基药/监控/集采/抗生素；与药品性质分框）。
     */
    @Schema(description = "重点药类型选项")
    private List<OptionDto> keyDrugTypeOptions;

    /**
     * 药品性质选项（麻醉/精一/精二；与重点药类型分框）。
     */
    @Schema(description = "药品性质选项")
    private List<OptionDto> medicinePropertyOptions;
}
