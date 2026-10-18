/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.chinesemedicine.response;

import com.zxplat.application.inpatient.doctorstation.chinesemedicine.dto.TcmRxHistoryRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-IP-TCM-39：患者历史中药处方响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "患者历史中药处方响应")
public class ListPatientTcmHerbalPrescriptionHistoryResponse {

    /** 历史方摘要列表。 */
    @Schema(description = "历史方摘要列表")
    private List<TcmRxHistoryRowDto> historyRows = new ArrayList<>();
}
