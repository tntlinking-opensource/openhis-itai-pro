/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.deptissue.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 科室领用页初始化请求（IF-DEP-IS-01）。
 *
 * <p>机构维度由应用层 {@link com.zxplat.application.shared.validator.CurrentManagedOrganizationIdsResolver} 解析，请求体可为空。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室领用页初始化 IF-DEP-IS-01")
public class LoadDeptIssueFormInitRequest {
}
