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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量取消执行（诊疗/药品登记抵消与耗材发放回退）请求（IF-NUR-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量取消执行请求")
public class BatchCancelDispositionRequest {

    /**
     * 就诊主键：批量取消必须限定在同一就诊，禁止跨就诊混批（AS §7）。
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
     * 取消原因：写入 {@code CancelProcedureRecordCommand#statusReasonText}（BR-PRO-005）。
     */
    @NotBlank(message = "statusReasonText 不能为空")
    @Schema(description = "取消原因", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusReasonText;

    /**
     * 勾选的净剩已完成执行记录主键列表（每条对应一次 CANCEL 抵消，{@code refund_id} 指向该 id）。
     */
    @Schema(description = "勾选的已完成执行记录主键列表（procedureId）")
    private List<Long> procedureIds;

    /**
     * 勾选的已发放耗材发放行主键列表（耗材取消语义独立，见 BR-NUR-012）。
     */
    @Schema(description = "勾选的已发放耗材发放行主键列表")
    private List<Long> deviceDispenseIds;
}
