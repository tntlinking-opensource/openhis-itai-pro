/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusstudent.response;

import com.zxplat.plugin.campus.application.campusarchive.response.ArchiveTransferDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageStudentTransfersResponse extends PagedResult<ArchiveTransferDto> {
}
