/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.response;

import com.zxplat.application.inpatient.financial.registration.dto.RegistrationEncounterDto;
import com.zxplat.application.inpatient.financial.registration.dto.RegistrationPatientDto;
import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 登记上下文加载响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院登记上下文")
public class LoadRegistrationContextResponse {

    /** 患者主档（业务字段）。 */
    @Schema(description = "患者主档")
    private RegistrationPatientDto patient;

    /** 住院就诊/申请信息；无 encounterId 时为 null。 */
    @Schema(description = "住院就诊/申请信息；无 encounterId 时为 null")
    private RegistrationEncounterDto encounter;

    /** 费用性质下拉。 */
    @Schema(description = "费用性质下拉")
    private List<OptionDto> contractOptions;

    /** 入院科室下拉。 */
    @Schema(description = "入院科室下拉（类别=住院的启用科室）")
    private List<OptionDto> organizationOptions;

    /** 入院方式下拉（术语 in_way_code）。 */
    @Schema(description = "入院方式下拉")
    private List<OptionDto> inWayCodeOptions;

    /** 入院类型下拉（术语 admit_source_code）。 */
    @Schema(description = "入院类型下拉")
    private List<OptionDto> admitSourceCodeOptions;

    /** 患者病情下拉（术语 adm_cond_code，与 {@code adm_encounter.priority_enum} 同源）。 */
    @Schema(description = "患者病情下拉")
    private List<OptionDto> admCondCodeOptions;

    /** 查看模式为 true 时不可编辑。 */
    @Schema(description = "查看模式为 true 时不可编辑")
    private Boolean readOnly;

    /** 是否允许自费转医保（BR-IP-REG-019）。 */
    @Schema(description = "是否允许自费转医保")
    private Boolean switchToInsuranceEnabled;

    /** 是否允许医保转自费（IF-IP-REG-09）。 */
    @Schema(description = "是否允许医保转自费")
    private Boolean switchToSelfPayEnabled;
}
