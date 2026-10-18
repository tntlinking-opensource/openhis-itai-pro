/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.prescriptionreview.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 点评记录页初始化响应（IF-PRV-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "点评记录页初始化响应")
public class LoadPageInitResponse {

    /** 点评状态下拉（PENDING / REVIEWED）。 */
    @Schema(description = "点评状态下拉")
    private List<OptionDto> statusOptions;

    /** 是否合理下拉（合理 / 不合理）。 */
    @Schema(description = "是否合理下拉")
    private List<OptionDto> reasonableOptions;

    /**
     * 重点药类型下拉（基药 / 国家重点监控 / 集采 / 抗生素）。
     *
     * <p>value = {@code KeyDrugType} code（BASIC/NATIONAL_MONITOR/CENTRAL_PURCHASE/ANTIBIOTIC）。
     * 麻/精见 {@link #medicinePropertyOptions}。</p>
     */
    @Schema(description = "重点药类型下拉")
    private List<OptionDto> keyDrugTypeOptions;

    /**
     * 药品性质下拉（麻醉药品 / 一类精神药品 / 二类精神药品）。
     *
     * <p>value = {@code KeyDrugType} code（NARCOTIC/PSYCH_ONE/PSYCH_TWO）。</p>
     */
    @Schema(description = "药品性质下拉")
    private List<OptionDto> medicinePropertyOptions;

    /** 默认点评日起（yyyy-MM-dd，本月首日）。 */
    @Schema(description = "默认点评日起")
    private String defaultReviewedDateFrom;

    /** 默认点评日止（yyyy-MM-dd，本月末日）。 */
    @Schema(description = "默认点评日止")
    private String defaultReviewedDateTo;
}
