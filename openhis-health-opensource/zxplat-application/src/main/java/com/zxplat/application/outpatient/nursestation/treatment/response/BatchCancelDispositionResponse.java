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
 * 批量取消执行响应（IF-NUR-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量取消执行响应")
public class BatchCancelDispositionResponse {

    /**
     * 是否整批成功（任一 Cmd 抛错则整体回滚并由上层捕获为失败）。
     */
    @Schema(description = "是否整批成功")
    private Boolean success;

    /**
     * 实际完成抵消的已完成执行记录主键列表（与请求 {@code procedureIds} 子集对应）。
     */
    @Schema(description = "实际完成抵消的已完成执行记录主键列表")
    private List<Long> cancelledProcedureIds;

    /**
     * 实际回退准备中的耗材发放行主键列表。
     */
    @Schema(description = "实际回退准备中的耗材发放行主键列表")
    private List<Long> revertedDeviceDispenseIds;
}
