/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusstudent.response;

import com.openhis.campus.application.campusarchive.response.ArchiveTransferDto;
import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageStudentTransfersResponse extends PagedResult<ArchiveTransferDto> {
}
