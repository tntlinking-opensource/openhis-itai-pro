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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-IP-PRC-04：医嘱补费确认响应。
 *
 * <p>字段语义与 {@link ConfirmDirectNurseStationPricingResponse} 相同；写路径 based_on 已写入父医嘱。</p>
 */
@Data
@Accessors(chain = true)
/** 医嘱补费确认响应。 */
@Schema(description = "医嘱补费确认响应")
public class ConfirmSupplementNurseStationPricingResponse {

    @Schema(description = "整批成功")
    private Boolean success;

    @Schema(description = "成功行数")
    private Integer processedCount;

    /** 本批新建的诊疗请求 id。 */
    @Schema(description = "新建诊疗请求 id")
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> createdServiceRequestIds = new ArrayList<>();

    /** 本批新建的耗材请求 id。 */
    @Schema(description = "新建耗材请求 id")
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> createdDeviceRequestIds = new ArrayList<>();
}
