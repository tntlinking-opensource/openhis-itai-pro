/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.response;

import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.dto.TcmDiagnosisCatalogRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** IF-DOR-31：分页检索中医病名/证候响应。 */
@Data
@Accessors(chain = true)
@Schema(description = "分页检索中医病名证候响应")
public class PageTcmConditionDefinitionResponse {
    /** 目录行。 */
    @Schema(description = "目录行")
    private List<TcmDiagnosisCatalogRowDto> rows;
    /** 总条数。 */
    @Schema(description = "总条数")
    private Long total;
}
