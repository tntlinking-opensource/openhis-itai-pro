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

package com.openhis.health.application.inpatient.doctorstation.advice.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * IF-IP-DOR-19：按诊疗活动定义查询可执行科室/地点规则（须与住院就诊、患者门控一致）。
 */
@Data
@Schema(description = "医生站-按诊疗定义查询执行科室规则")
public class ListActivityExecutionLocationsRequest {

    /** 当前住院就诊主键，用于 {@code InpatientInfo} 门控。 */
    @Schema(description = "就诊主键")
    private Long encounterId;

    /** 患者主键，须与就诊读模型中的 {@code patientId} 一致。 */
    @Schema(description = "患者主键（须与就诊绑定一致）")
    private Long patientId;

    /** 诊疗活动定义（{@code wkf_activity_definition}）主键，决定规则查询范围。 */
    @Schema(description = "诊疗活动定义主键")
    private Long activityDefinitionId;
}
