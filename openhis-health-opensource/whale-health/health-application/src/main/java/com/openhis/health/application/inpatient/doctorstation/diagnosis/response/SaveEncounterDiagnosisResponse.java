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

package com.openhis.health.application.inpatient.doctorstation.diagnosis.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-05：保存住院就诊诊断响应（AS-IP-DOR-002）。
 *
 * <p>返回成功条数与诊断事实主键列表，顺序与请求批次子项一致，供前端回写表格行锚点。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-保存就诊诊断响应")
public class SaveEncounterDiagnosisResponse {

    /**
     * 成功持久化的诊断事实条数。
     */
    @Schema(description = "保存成功条数")
    private Integer savedCount;

    /**
     * 保存后的诊断事实主键列表：与入参 {@code diagnosisChildList} 顺序一一对应。
     */
    @Schema(description = "保存后的诊断事实标识列表")
    private List<Long> savedConditionIds;
}
