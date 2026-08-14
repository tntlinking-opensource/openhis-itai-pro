/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.nurseworkload.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 护士工作量统计页初始化入参（IF-RPT-NWL-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "护士工作量统计页初始化 IF-RPT-NWL-01")
public class LoadNurseWorkloadFormInitRequest {

    /**
     * 就诊范围 OUTPATIENT/INPATIENT；空则默认门诊，用于刷新开立科室下拉。
     */
    @Schema(description = "就诊范围 OUTPATIENT/INPATIENT")
    private String encounterScope;
}
