/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import com.zxplat.domain.share.constants.DoctorStationAdviceConstants;
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
@Schema(description = "医生站-医嘱开立-查询医嘱绑定信息请求")
public class GetInpatientDoctorStationAdviceOrderBindInfoRequest {

    /** 绑定来源类型：USAGE（用法）/ ACTIVITY（诊疗）。 */
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
