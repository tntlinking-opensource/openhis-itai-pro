/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.deptissuereturn.response;

import com.zxplat.application.inventory.department.deptissuereturn.dto.LinkableIssueListRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 可关联领用单分页响应（IF-DEP-TL-12）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "可关联领用单分页 IF-DEP-TL-12")
public class PageLinkableIssueDocumentsResponse extends PagedResult<LinkableIssueListRowDto> {
}
