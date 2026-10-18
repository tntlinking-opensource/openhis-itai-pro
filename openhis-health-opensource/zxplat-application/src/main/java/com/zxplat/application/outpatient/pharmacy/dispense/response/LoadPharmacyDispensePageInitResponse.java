/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.dispense.response;

import com.zxplat.application.outpatient.pharmacy.dispense.dto.PractitionerOptionDto;
import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 门诊发药页初始化响应（IF-OPP-01）。
 *
 * <p>下拉项统一 {@link com.zxplat.framework.core.application.dto.OptionDto}；药师候选行为扩展列 {@link com.zxplat.application.outpatient.pharmacy.dispense.dto.PractitionerOptionDto}。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊发药页初始化响应")
public class LoadPharmacyDispensePageInitResponse {

    @Schema(description = "可操作药房位置选项（adm_location.id）")
    private List<OptionDto> pharmacyOrganizationOptions;

    @Schema(description = "发放状态筛选项")
    private List<OptionDto> dispenseStatusFilterOptions;

    /** 就诊类别筛选下拉。 */
    @Schema(description = "就诊类别选项")
    private List<OptionDto> encounterClassOptions;

    /** 未发药/作废原因下拉（术语未就绪时可为空列表）。 */
    @Schema(description = "未发药原因选项（目标态枚举未发布时可为空）")
    private List<OptionDto> notPerformedReasonOptions;

    /** 调配药师分页候选（含机构上下文列）。 */
    @Schema(description = "调配药师候选分页")
    private Page<PractitionerOptionDto> preparerCandidatePage;
}
