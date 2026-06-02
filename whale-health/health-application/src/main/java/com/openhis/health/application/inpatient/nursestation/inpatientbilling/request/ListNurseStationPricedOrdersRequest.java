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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IF-IP-PRC-05：已划价列表请求（BR-IP-PRC-015）。
 *
 * <p>仅 nurse-pricing 来源的护士划价 OrderInfo；可按时间窗与父医嘱 id 收窄补费子行。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "已划价列表请求")
public class ListNurseStationPricedOrdersRequest {

    /** 左侧多选就诊；服务端收窄为管理病区可见集合。 */
    @NotEmpty(message = "就诊列表不能为空")
    @Schema(description = "多选就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> encounterIds;

    /** 划价时间下限（含，对应 OrderInfo.createdAt）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "划价时间起")
    private LocalDateTime pricedTimeFrom;

    /** 划价时间上限（含）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "划价时间止")
    private LocalDateTime pricedTimeTo;

    /** 父医嘱 requestId；仅返回 based_on 指向该父的补费划价行。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "父医嘱 id 过滤（补费子行）")
    private Long parentRequestId;
}
