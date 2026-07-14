/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissue.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 科室领用页初始化请求（IF-DEP-IS-01）。
 *
 * <p>机构维度由应用层 {@link com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver} 解析，请求体可为空。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室领用页初始化 IF-DEP-IS-01")
public class LoadDeptIssueFormInitRequest {

    /** 兼容字段，服务端忽略。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "兼容字段，服务端忽略")
    private Long organizationId;
}
