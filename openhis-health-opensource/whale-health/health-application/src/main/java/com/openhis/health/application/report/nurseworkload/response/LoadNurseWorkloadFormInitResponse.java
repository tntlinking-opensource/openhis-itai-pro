/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.nurseworkload.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.util.List;

/**
 * 护士工作量统计页初始化出参（IF-RPT-NWL-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "护士工作量统计页初始化 IF-RPT-NWL-01")
public class LoadNurseWorkloadFormInitResponse {

    /** 默认就诊范围。 */
    @Schema(description = "默认就诊范围")
    private String defaultEncounterScope;

    /** 默认统计日起。 */
    @Schema(description = "默认统计日起")
    private LocalDate defaultDateFrom;

    /** 默认统计日止。 */
    @Schema(description = "默认统计日止")
    private LocalDate defaultDateTo;

    /** 就诊范围下拉。 */
    @Schema(description = "就诊范围下拉")
    private List<OptionDto> encounterScopeOptions;

    /** 护士下拉。 */
    @Schema(description = "护士下拉")
    private List<OptionDto> nurseOptions;

    /**
     * 默认登录科室下拉（门诊挂号科室 / 住院入院科室，随就诊范围；对应 {@code Practitioner.org_id}）。
     */
    @Schema(description = "默认登录科室下拉")
    private List<OptionDto> organizationOptions;
}
