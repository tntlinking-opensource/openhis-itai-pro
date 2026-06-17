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

package com.openhis.health.application.outpatient.doctorstation.diagnosis.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-08：保存就诊诊断响应。
 */
@Data
@Accessors(chain = true)
/** 医生站-保存就诊诊断响应。 */
@Schema(description = "医生站-保存就诊诊断响应")
public class SaveEncounterDiagnosisResponse {

    @Schema(description = "保存成功条数")
    private Integer savedCount;

    @Schema(description = "保存后的诊断事实标识列表")
    private List<Long> savedConditionIds;
}
