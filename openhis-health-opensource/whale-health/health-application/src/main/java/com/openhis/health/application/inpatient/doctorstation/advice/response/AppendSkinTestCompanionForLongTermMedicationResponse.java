/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-27：追加皮试检查响应（新建或复用的 companion SR 主键）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医生站-长期皮试药追加皮试检查响应")
public class AppendSkinTestCompanionForLongTermMedicationResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 提示文案。 */
    @Schema(description = "提示文案")
    private String message;

    /**
     * 新建或复用的 companion「皮试检查」{@code wkf_service_request.id} 列表；
     * 与请求 {@code medicationRequestIds} 顺序对齐（跳过空 id 时可能短于入参）。
     */
    @Schema(description = "companion 皮试检查诊疗申请主键列表")
    private List<Long> serviceRequestIds;
}
