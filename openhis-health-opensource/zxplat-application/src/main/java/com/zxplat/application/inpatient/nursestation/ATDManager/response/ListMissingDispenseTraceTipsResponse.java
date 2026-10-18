/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.ATDManager.response;

import com.zxplat.application.inpatient.nursestation.ATDManager.dto.MissingDispenseTraceTipDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-IP-NUR-ATD-06a：清床前缺追溯码软提示查询响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "缺追溯码发药提示查询响应")
public class ListMissingDispenseTraceTipsResponse {

    /** 是否存在缺追溯码的已核发行。 */
    @Schema(description = "是否存在缺追溯码行")
    private Boolean hasMissingTrace;

    /** 缺追溯码提示行（按发药日+药品）；空表示无需提示。 */
    @Schema(description = "缺追溯码提示行")
    private List<MissingDispenseTraceTipDto> tips = new ArrayList<>();
}
