/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.deptdesk.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 保存分科结论请求（IF-PEIS-DES-04）。
 */
@Data
public class SaveDeptConclusionRequest {

    /** 服务申请主键。 */
    @Schema(description = "服务申请主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long serviceRequestId;

    /** 分科结论观察定义主键。 */
    @Schema(description = "分科结论观察定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long definitionId;

    /** 科室机构主键（权限校验）。 */
    @Schema(description = "科室机构主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deptOrgId;

    /** 结论文本。 */
    @Schema(description = "结论文本")
    private String valueString;

    /** 诊断定义模板主键（可选）。 */
    @Schema(description = "诊断定义模板主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conditionDefinitionId;
}
