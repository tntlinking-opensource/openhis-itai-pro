/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.nursingrecord.response;

import com.openhis.health.application.inpatient.nursestation.nursingrecord.dto.NursingRecordRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-NRC-04：批量保存护理记录响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量保存护理记录响应")
public class SaveNurseStationNursingRecordsBatchResponse {

    /** 成功保存行数。 */
    @Schema(description = "成功保存行数")
    private Integer savedCount;

    /** 保存后的行列表。 */
    @Schema(description = "保存后的行列表")
    private List<NursingRecordRowDto> rows;
}
