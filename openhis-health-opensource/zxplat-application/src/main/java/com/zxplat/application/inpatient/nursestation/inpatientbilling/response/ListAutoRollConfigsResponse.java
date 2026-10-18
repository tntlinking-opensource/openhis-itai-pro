/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.response;

import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationAutoRollConfigRowDto;
import com.zxplat.application.inpatient.nursestation.inpatientbilling.dto.NurseStationAutoRollNursingReadonlyDto;
import com.zxplat.framework.core.application.dto.OptionDto;
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

    /**
     * 护理级别只读区（兼容字段：取 {@link #nursingLevels} 首行，通常为当前有效级别）。
     */
    @Schema(description = "护理级别只读区（兼容：等同 nursingLevels 首行）")
    private NurseStationAutoRollNursingReadonlyDto nursingLevelReadonly;

    /**
     * 护理级别只读列表（可展开查看已滚费用；billDate 空时含历史已停嘱行）。
     */
    @Schema(description = "护理级别只读列表")
    private List<NurseStationAutoRollNursingReadonlyDto> nursingLevels = new ArrayList<>();

    /**
     * 计费人下拉（在职护士；供配置弹窗选用）。
     */
    @Schema(description = "计费人护士选项")
    private List<OptionDto> nurseOptions = new ArrayList<>();
}
