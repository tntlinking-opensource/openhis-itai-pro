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
 * <p>按用法 {@code method_code} 或诊疗活动定义主键拉取绑定模板行，供开立弹窗展示关联耗材/套餐子项。</p>
 */
@Data
@Accessors(chain = true)
/** 医生站-医嘱开立-查询医嘱绑定信息请求。 */
@Schema(description = "医生站-医嘱开立-查询医嘱绑定信息请求")
public class GetInpatientDoctorStationAdviceOrderBindInfoRequest {

    /** 绑定来源类型：{@code USAGE} / {@code ACTIVITY}，与模板 {@code type_code} 同码。 */
    @NotBlank(message = DoctorStationAdviceConstants.VALIDATION_BIND_TYPE_REQUIRED)
    @Schema(description = "绑定来源类型：USAGE（用法）/ ACTIVITY（诊疗）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String bindType;

    /** 主数据来源键字符串：用法绑定为 {@code method_code}；诊疗绑定为活动定义主键字符串。 */
    @NotBlank(message = DoctorStationAdviceConstants.VALIDATION_BIND_SOURCE_ITEM_NO_REQUIRED)
    /** 用法 method_code 或诊疗活动定义主键字符串。 */
    @Schema(description = "用法 method_code 或诊疗活动定义主键字符串", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemNo;

    /** 诊疗活动定义主键（仅 {@code ACTIVITY} 绑定可选，与 itemNo 二选一）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 主数据主键。 */
    @Schema(description = "主数据主键")
    private Long itemId;

    /** 机构单元过滤。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 机构单元过滤。 */
    @Schema(description = "机构单元过滤")
    private Long organizationUnitId;
}
