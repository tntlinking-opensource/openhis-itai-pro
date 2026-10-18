/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.surgeryanesth.response;

import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.ClinicalProcV3SearchItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * AS-IP-DOR-012 响应：SearchClinicalProcV3。
 */
@Data
@Accessors(chain = true)
@Schema(description = "SearchClinicalProcV3Response")
public class SearchClinicalProcV3Response {

    /** 检索结果。 */
    @Schema(description = "检索结果")
    private List<ClinicalProcV3SearchItemDto> items;

    /** 总数（分页）。 */
    @Schema(description = "总数")
    private Long totalCount;
}
