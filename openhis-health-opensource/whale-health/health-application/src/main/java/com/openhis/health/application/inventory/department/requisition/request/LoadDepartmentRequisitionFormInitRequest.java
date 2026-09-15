/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.requisition.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 科室药房请领页初始化请求（IF-DEP-QL-01）。
 *
 * <p>机构上下文由 {@code CurrentManagedOrganizationIdsResolver} 从会话解析，请求体可空。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室药房请领页初始化请求 IF-DEP-QL-01")
public class LoadDepartmentRequisitionFormInitRequest {
}
