/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.surgeryanesth.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * AS-IP-DOR-012 响应：InitSurgeryAnesth。
 */
@Data
@Accessors(chain = true)
@Schema(description = "InitSurgeryAnesthResponse")
public class InitSurgeryAnesthResponse {

    /** 手术级别下拉。 */
    @Schema(description = "手术级别下拉")
    private List<OptionDto> procedureLevelOptions;

    /** 切口类别下拉。 */
    @Schema(description = "切口类别下拉")
    private List<OptionDto> incisionTypeOptions;

    /** 麻醉方法下拉。 */
    @Schema(description = "麻醉方法下拉")
    private List<OptionDto> anesthesiaMethodOptions;

    /** 是/否下拉。 */
    @Schema(description = "是/否下拉")
    private List<OptionDto> yesNoOptions;
}
