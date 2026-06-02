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

import com.openhis.health.application.emergency.emgtriage.station.request.SaveOrSubmitEmgTriageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 分诊详情回显：与 IF-EMG-06 请求体同构，便于前端直接绑定编辑区。
 */
@Data
@Accessors(chain = true)
@Schema(description = "急诊分诊-详情（编辑回显）")
public class GetEmgTriageDetailForEditResponse {

    @Schema(description = "分诊事实载荷（与 saveOrSubmitEmgTriage 请求字段一致）")
    private SaveOrSubmitEmgTriageRequest detail;

    @Schema(description = "患者姓名（展示用）")
    private String patientName;
}
