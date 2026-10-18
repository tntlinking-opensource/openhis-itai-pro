/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.nursingrecord.response;

import com.zxplat.application.inpatient.nursestation.nursingrecord.dto.NursingRecordPaperHeaderDto;
import com.zxplat.application.inpatient.nursestation.nursingrecord.dto.NursingRecordRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-NRC-06：护理记录打印读侧响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "护理记录打印数据响应")
public class GetNurseStationNursingRecordPrintDataResponse {

    /** 打印模板 code。 */
    @Schema(description = "打印模板 code")
    private String templateCode;

    /** 护理记录行列表。 */
    @Schema(description = "护理记录行列表")
    private List<NursingRecordRowDto> rows;

    /** 纸质单抬头。 */
    @Schema(description = "纸质单抬头")
    private NursingRecordPaperHeaderDto paperHeader;
}
