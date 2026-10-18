/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusstaff.response;

import com.zxplat.plugin.campus.application.campusarchive.response.ArchiveChangeLogDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageStaffChangesResponse extends PagedResult<ArchiveChangeLogDto> {
}
