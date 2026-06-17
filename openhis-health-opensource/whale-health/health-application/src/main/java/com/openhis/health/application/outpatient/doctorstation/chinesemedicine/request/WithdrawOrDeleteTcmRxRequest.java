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

package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-38：签退或删除中药处方请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "签退或删除中药处方请求")
public class WithdrawOrDeleteTcmRxRequest {

    /** 就诊主键。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者主键。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 动作：WITHDRAW 签退 / DELETE 删除。 */
    @NotBlank(message = "action 不能为空")
    @Schema(description = "动作 WITHDRAW/DELETE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;

    /** 范围：PRESCRIPTION 整方 / LINE 单行。 */
    @NotBlank(message = "scope 不能为空")
    @Schema(description = "范围 PRESCRIPTION/LINE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String scope;

    /** 目标药品申请主键列表。 */
    @NotEmpty(message = "medicationRequestIds 不能为空")
    @Schema(description = "目标药品申请主键列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> medicationRequestIds;

    /** 整方时携带的前端方标识。 */
    @Schema(description = "整方时携带的前端方标识")
    private String prescriptionGroupKey;
}
