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

package com.openhis.health.application.inpatient.doctorstation.advice.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-20：停嘱（长期医嘱）批次处理结果。
 *
 * <p>含成功标识与提示文案；失败时整批事务回滚。</p>
 */
@Data
@Accessors(chain = true)
/** 住院医生站-长期医嘱停嘱响应。 */
@Schema(description = "住院医生站-长期医嘱停嘱响应")
public class StopLongTermInpatientDoctorStationAdviceResponse {

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "提示文案")
    private String message;
}
