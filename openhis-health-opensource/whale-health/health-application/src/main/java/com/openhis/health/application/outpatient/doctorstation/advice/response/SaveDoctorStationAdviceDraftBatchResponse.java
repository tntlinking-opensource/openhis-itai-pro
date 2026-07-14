/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-14：暂存医嘱变更批次响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-暂存医嘱变更批次响应")
public class SaveDoctorStationAdviceDraftBatchResponse {

    @Schema(description = "是否成功")
    /** 本次新建药品请求主键列表。 */
    private Boolean success;

    @Schema(description = "本次新建药品请求主键列表")
    private List<Long> newMedicationRequestIds;

    /** 提示信息。 */
    @Schema(description = "提示信息")
    private String message;
}
