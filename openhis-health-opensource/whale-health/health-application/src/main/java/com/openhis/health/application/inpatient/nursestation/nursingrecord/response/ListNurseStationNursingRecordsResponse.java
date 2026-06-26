/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.nursingrecord.response;

import com.openhis.health.application.inpatient.nursestation.nursingrecord.dto.NursingRecordPaperHeaderDto;
import com.openhis.health.application.inpatient.nursestation.nursingrecord.dto.NursingRecordRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-NRC-02：列表护理记录响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "列表护理记录响应")
public class ListNurseStationNursingRecordsResponse {

    /** 护理记录行列表。 */
    @Schema(description = "护理记录行列表")
    private List<NursingRecordRowDto> rows;

    /** 纸质单抬头。 */
    @Schema(description = "纸质单抬头")
    private NursingRecordPaperHeaderDto paperHeader;
}
