/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.department.deptreturn.response;

import com.openhis.health.application.inventory.department.deptreturn.dto.DeptReturnListRowDto;
import com.openhis.health.module.common.pagination.PagedResult;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageLinkableReturnSourceDocumentsResponse extends PagedResult<DeptReturnListRowDto> {
}
