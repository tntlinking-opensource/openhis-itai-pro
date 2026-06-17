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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-14：暂存医嘱变更批次响应。
 *
 * <p>返回各行落库后的资源主键（CREATE 行）及统一成功标识，供前端回写表格行 id。</p>
 */
@Data
@Accessors(chain = true)
/** 医生站-医嘱开立-暂存医嘱变更批次响应。 */
@Schema(description = "医生站-医嘱开立-暂存医嘱变更批次响应")
public class SaveInpatientDoctorStationAdviceDraftBatchResponse {

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "本次新建药品请求主键列表")
    private List<Long> newMedicationRequestIds;

    /** 人类可读提示或错误摘要。 */
    @Schema(description = "提示信息")
    private String message;
}
