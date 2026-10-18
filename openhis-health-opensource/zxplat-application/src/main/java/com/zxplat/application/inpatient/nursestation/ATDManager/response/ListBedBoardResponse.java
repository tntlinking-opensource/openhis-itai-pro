/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.ATDManager.response;

import com.zxplat.application.inpatient.nursestation.ATDManager.dto.BedBoardItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-NUR-ATD-01：床位列表查询响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "床位列表查询响应")
public class ListBedBoardResponse {

    @Schema(description = "床位列表项")
    /** 床位列表项。 */
    private List<BedBoardItemDto> items;
}
