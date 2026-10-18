/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.chinesemedicine.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-IP-TCM-36：暂存中药处方响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "暂存中药处方响应")
public class SaveTcmHerbalPrescriptionDraftBatchResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 新建药品申请 id 列表。 */
    @Schema(description = "新建药品申请 id 列表")
    private List<Long> newMedicationRequestIds = new ArrayList<>();

    /** 提示信息。 */
    @Schema(description = "提示信息")
    private String message;
}
