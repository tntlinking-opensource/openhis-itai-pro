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
 * IF-IP-PRC-06：取消划价响应。
 *
 * <p>整批成功或整批回滚；补费父医嘱不变（BR-IP-PRC-027）。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "取消划价响应")
public class CancelNurseStationPricingResponse {

    /** 整批取消是否成功。 */
    @Schema(description = "整批成功")
    private Boolean success;

    /** 成功取消的行数。 */
    @Schema(description = "成功取消行数")
    private Integer processedCount;

    /** 已软删的 wkf_service_request id 列表。 */
    @Schema(description = "已取消诊疗请求 id")
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> cancelledServiceRequestIds = new ArrayList<>();

    /** 已软删的 wkf_device_request id 列表。 */
    @Schema(description = "已取消耗材请求 id")
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> cancelledDeviceRequestIds = new ArrayList<>();
}
