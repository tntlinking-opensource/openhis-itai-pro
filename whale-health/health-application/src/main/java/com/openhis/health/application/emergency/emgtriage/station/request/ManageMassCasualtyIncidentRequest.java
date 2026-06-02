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

package com.openhis.health.application.emergency.emgtriage.station.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-EMG-19：群伤事件管理（领域服务目标态暂未落地时的占位契约）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "急诊分诊-群伤事件管理请求")
public class ManageMassCasualtyIncidentRequest {

    @NotBlank
    @Schema(description = "动作：SAVE_INCIDENT / BIND / UNBIND", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "事件主键（更新/绑定）")
    private Long incidentId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "批量绑定的患者标识")
    private List<Long> patientIds = new ArrayList<>();

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "解绑目标患者")
    private Long unbindPatientId;
}
