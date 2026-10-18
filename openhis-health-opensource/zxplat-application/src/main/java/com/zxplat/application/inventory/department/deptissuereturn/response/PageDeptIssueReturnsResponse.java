/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.deptissuereturn.response;

import com.zxplat.application.inventory.department.deptissuereturn.dto.DeptIssueReturnListRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 科室退领单分页列表响应（IF-DEP-TL-02）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室退领单分页列表 IF-DEP-TL-02")
public class PageDeptIssueReturnsResponse extends PagedResult<DeptIssueReturnListRowDto> {
}
