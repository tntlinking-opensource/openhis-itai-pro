/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.nursingrecord.response;

import com.zxplat.application.inpatient.nursestation.nursingrecord.dto.NursingRecordRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-NRC-03：保存单条护理记录响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "保存单条护理记录响应")
public class SaveNurseStationNursingRecordResponse {

    /** 保存后的护理记录行。 */
    @Schema(description = "保存后的护理记录行")
    private NursingRecordRowDto row;
}
