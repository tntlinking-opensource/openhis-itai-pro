/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.outpatient.nursestation.response;

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
