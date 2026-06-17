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
 * IF-IP-DOR-16：签退医嘱批次响应。
 *
 * <p>整批成功标识与统一文案 message；失败时由事务整体回滚。</p>
 */
@Data
@Accessors(chain = true)
/** 医生站-医嘱开立-签退医嘱批次响应。 */
@Schema(description = "医生站-医嘱开立-签退医嘱批次响应")
public class WithdrawInpatientDoctorStationAdviceBatchResponse {

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "提示信息")
    private String message;
}
