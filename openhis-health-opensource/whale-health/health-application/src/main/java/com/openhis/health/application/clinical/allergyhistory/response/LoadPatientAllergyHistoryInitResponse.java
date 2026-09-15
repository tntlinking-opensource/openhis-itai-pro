/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.clinical.allergyhistory.response;

import com.openhis.health.application.clinical.allergyhistory.dto.PatientAllergyHistoryEnumOptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 过敏史表单选项响应。
 */
@Getter
@Setter
public class LoadPatientAllergyHistoryInitResponse {

    /** 过敏原类别选项。 */
    private List<PatientAllergyHistoryEnumOptionDto> categoryOptions;

    /** 验证状态选项。 */
    private List<PatientAllergyHistoryEnumOptionDto> verificationStatusOptions;

    /** 严重程度选项。 */
    private List<PatientAllergyHistoryEnumOptionDto> severityOptions;

    /** 危险程度选项。 */
    private List<PatientAllergyHistoryEnumOptionDto> criticalityOptions;
}
