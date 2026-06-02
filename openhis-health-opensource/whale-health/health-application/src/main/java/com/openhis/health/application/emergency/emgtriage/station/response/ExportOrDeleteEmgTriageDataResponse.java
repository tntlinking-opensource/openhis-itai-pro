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

package com.openhis.health.application.emergency.emgtriage.station.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-EMG-21：导出或删除分诊数据响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "急诊分诊-导出或删除响应")
public class ExportOrDeleteEmgTriageDataResponse {

    @Schema(description = "异步导出文件 URL（占位）")
    private String exportFileUrl;

    @Schema(description = "作废是否完成")
    private Boolean voided;
}
