/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.returnfromdownstream.response;

import com.openhis.health.application.inventory.warehouse.returnfromdownstream.dto.ReturnFromDownstreamListRowDto;
import com.openhis.health.module.common.pagination.PagedResult;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageLinkableDeliveriesResponse extends PagedResult<ReturnFromDownstreamListRowDto> {
}
