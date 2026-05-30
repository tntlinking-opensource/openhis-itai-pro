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

package com.openhis.health.application.outpatient.nursestation.dto;

import com.openhis.health.application.outpatient.nursestation.response.BatchPerformDispositionResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 批量处置逐行结果占位 DTO（AS-SDD §2.1 包结构）。
 * <p>当前 {@link BatchPerformDispositionResponse} 仅以 id 列表聚合结果；逐行明细字段待 AS 扩展后再落库到本类，避免与未定契约冲突。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量处置逐行结果（预留扩展，暂无业务字段）")
public class BatchPerformDispositionResultItemDto {
}
