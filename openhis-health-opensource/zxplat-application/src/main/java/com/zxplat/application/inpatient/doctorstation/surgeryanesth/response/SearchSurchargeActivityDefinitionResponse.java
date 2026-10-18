/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.surgeryanesth.response;

import com.zxplat.application.inpatient.doctorstation.surgeryanesth.dto.SurchargeActivityDefinitionItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * AS-IP-DOR-012 响应：SearchSurchargeActivityDefinition。
 */
@Data
@Accessors(chain = true)
@Schema(description = "SearchSurchargeActivityDefinitionResponse")
public class SearchSurchargeActivityDefinitionResponse {

    /** 加收项目。 */
    @Schema(description = "加收项目")
    private List<SurchargeActivityDefinitionItemDto> items;

    /** 总数。 */
    @Schema(description = "总数")
    private Long totalCount;
}
