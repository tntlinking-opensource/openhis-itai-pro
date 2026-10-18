/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.nursestation.treatment.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量处置执行响应（IF-NUR-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量处置执行响应")
public class BatchPerformDispositionResponse {

    /**
     * 是否整批成功（任一 Cmd 抛错则整体回滚并由上层捕获为失败）。
     */
    @Schema(description = "是否整批成功")
    private Boolean success;

    /**
     * 实际完成登记的诊疗请求主键列表。
     */
    @Schema(description = "实际完成登记的诊疗请求主键列表")
    private List<Long> performedServiceIds;

    /**
     * 实际完成登记的药品请求主键列表。
     */
    @Schema(description = "实际完成登记的药品请求主键列表")
    private List<Long> performedMedicationIds;

    /**
     * 完成发放的耗材发放行主键列表。
     */
    @Schema(description = "完成发放的耗材发放行主键列表")
    private List<Long> completedDeviceDispenseIds;
}
