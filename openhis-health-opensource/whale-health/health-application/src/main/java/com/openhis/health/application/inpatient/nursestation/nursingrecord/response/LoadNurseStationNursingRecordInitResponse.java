/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.nursingrecord.response;

import com.openhis.health.application.inpatient.nursestation.nursingrecord.dto.NursingRecordViewModeOptionDto;
import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-NRC-01：护理记录 Tab 页初始化响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "护理记录页初始化响应")
public class LoadNurseStationNursingRecordInitResponse {

    /** 视图模式选项。 */
    @Schema(description = "视图模式选项")
    private List<NursingRecordViewModeOptionDto> viewModeOptions;

    /** 常用体征项选项。 */
    @Schema(description = "常用体征项选项")
    private List<OptionDto> vitalSignItemOptions;

    /** 默认周区间说明。 */
    @Schema(description = "默认周区间说明")
    private String defaultWeekRangeDescription;
}
