/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DOR-12：查询医嘱绑定信息请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-查询医嘱绑定信息请求")
public class GetDoctorStationAdviceOrderBindInfoRequest {

    /** 绑定来源类型：USAGE（用法）/ ACTIVITY（诊疗）。 */
    @NotBlank(message = "绑定类型不能为空")
    @Schema(description = "绑定来源类型：USAGE（用法）/ ACTIVITY（诊疗）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String bindType;

    /** 主数据业务号或定义 id 的字符串形态。 */
    @NotBlank(message = "主数据项号不能为空")
    /** 主数据业务号或定义 id 的字符串形态。 */
    @Schema(description = "主数据业务号或定义 id 的字符串形态", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemNo;

    /** 主数据主键。 */
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
