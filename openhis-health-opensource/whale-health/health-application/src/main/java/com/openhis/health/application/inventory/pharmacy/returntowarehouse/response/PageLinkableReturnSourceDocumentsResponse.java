/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.returntowarehouse.response;

import com.openhis.health.application.inventory.pharmacy.returntowarehouse.dto.LinkablePharmacyStockInListRowDto;
import com.openhis.health.application.shared.pagination.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 可关联药房入库单分页响应（IF-PHA-RTW-12）。
 */
@Getter
@Setter
public class PageLinkableReturnSourceDocumentsResponse extends PagedResult<LinkablePharmacyStockInListRowDto> {
}
