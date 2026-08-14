/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.response;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationAutoRollConfigRowDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationAutoRollNursingReadonlyDto;
import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-IP-ARG-02：自动计费配置列表响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "自动计费配置列表响应")
public class ListAutoRollConfigsResponse {

    /** 配置列表。 */
    @Schema(description = "配置列表")
    private List<NurseStationAutoRollConfigRowDto> configs = new ArrayList<>();

    /** 护理级别只读区。 */
    @Schema(description = "护理级别只读区")
    private NurseStationAutoRollNursingReadonlyDto nursingLevelReadonly;

    /**
     * 计费人下拉（在职护士；供配置弹窗选用）。
     */
    @Schema(description = "计费人护士选项")
    private List<OptionDto> nurseOptions = new ArrayList<>();
}
