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

package com.openhis.health.application.outpatient.nursestation.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量处置执行（诊疗/药品登记与耗材发放）请求（IF-NUR-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量处置执行请求")
public class BatchPerformDispositionRequest {

    /**
     * 就诊主键：批量执行必须限定在同一就诊，禁止跨就诊混批（AS §7）。
     */
    @NotNull(message = "encounterId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /**
     * 操作护士主键：用于审计与领域命令 operatorId。
     */
    @NotNull(message = "operatorId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "操作护士主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long operatorId;

    /**
     * 覆盖执行人（可选）：允许入口指定执行护士/参与者。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "覆盖执行人（可选）")
    private Long performerPractitionerId;

    /**
     * 耗材房库位主键：仅对未锁定库房的待发发放行生效（BR-NUR-006）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "耗材房库位主键（仅未锁定库房行生效）")
    private Long inventoryLocationId;

    /**
     * 勾选的诊疗服务申请主键列表。
     */
    @Schema(description = "勾选的诊疗服务申请主键列表")
    private List<Long> serviceRequestIds;

    /**
     * 勾选的药品申请主键列表。
     */
    @Schema(description = "勾选的药品申请主键列表")
    private List<Long> medicationRequestIds;

    /**
     * 勾选的待发耗材发放行主键列表。
     */
    @Schema(description = "勾选的待发耗材发放行主键列表")
    private List<Long> deviceDispenseIds;
}
