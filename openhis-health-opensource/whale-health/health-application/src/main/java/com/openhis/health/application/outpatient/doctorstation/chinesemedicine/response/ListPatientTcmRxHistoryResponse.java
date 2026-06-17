/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.response;

import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.dto.TcmRxHistoryRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** IF-DOR-39：查询患者历史中药处方响应。 */
@Data
@Accessors(chain = true)
@Schema(description = "查询患者历史中药处方响应")
public class ListPatientTcmRxHistoryResponse {

    /** 历史方摘要列表。 */
    @Schema(description = "历史方摘要列表")
    private List<TcmRxHistoryRowDto> historyRows = new ArrayList<>();
}
