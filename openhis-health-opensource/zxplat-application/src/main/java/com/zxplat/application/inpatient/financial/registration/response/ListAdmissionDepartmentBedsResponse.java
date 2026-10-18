/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.response;

import com.zxplat.application.inpatient.financial.registration.dto.AdmissionDepartmentBedOptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 更正入院科室可选床列表。
 */
@Data
@Accessors(chain = true)
@Schema(description = "入院科室变更可选床响应")
public class ListAdmissionDepartmentBedsResponse {

    /** 空床，以及本就诊已在该病区占用的当前床。 */
    @Schema(description = "可选床")
    private List<AdmissionDepartmentBedOptionDto> beds = new ArrayList<>();
}
