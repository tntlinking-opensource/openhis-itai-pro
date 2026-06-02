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

package com.openhis.health.application.inpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-12：查询医嘱绑定信息请求。
 *
 * <p>按用法编码或诊疗活动定义主键拉取绑定模板行，供开立弹窗展示关联耗材/套餐子项。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-查询医嘱绑定信息请求")
public class GetInpatientDoctorStationAdviceOrderBindInfoRequest {

    /** 绑定类型。 */
    @NotBlank(message = DoctorStationAdviceConstants.VALIDATION_BIND_TYPE_REQUIRED)
    @Schema(description = "绑定类型", requiredMode = Schema.RequiredMode.REQUIRED)
    private String bindType;

    /** 主数据业务号或定义 id 的字符串形态。 */
    @NotBlank(message = DoctorStationAdviceConstants.VALIDATION_BIND_SOURCE_ITEM_NO_REQUIRED)
    @Schema(description = "主数据业务号或定义 id 的字符串形态", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemNo;

    /** 主数据主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "主数据主键")
    private Long itemId;

    /** 机构单元过滤。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "机构单元过滤")
    private Long organizationUnitId;
}
