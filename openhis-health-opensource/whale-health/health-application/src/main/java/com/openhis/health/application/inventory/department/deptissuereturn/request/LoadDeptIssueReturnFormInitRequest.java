/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissuereturn.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 科室退领页初始化请求（IF-DEP-TL-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室退领页初始化 IF-DEP-TL-01")
public class LoadDeptIssueReturnFormInitRequest {
}
