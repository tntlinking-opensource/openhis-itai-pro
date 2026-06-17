/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.pharmacy.returnupstream.response;

import com.openhis.health.application.inventory.pharmacy.returnupstream.dto.ReturnUpstreamListRowDto;
import com.openhis.health.module.common.pagination.PagedResult;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageReturnUpstreamsResponse extends PagedResult<ReturnUpstreamListRowDto> {
}
