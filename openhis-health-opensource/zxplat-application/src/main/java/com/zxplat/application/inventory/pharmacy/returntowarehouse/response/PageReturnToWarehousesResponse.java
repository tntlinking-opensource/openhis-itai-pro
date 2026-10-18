/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.returntowarehouse.response;

import com.zxplat.application.inventory.pharmacy.returntowarehouse.dto.ReturnToWarehouseListRowDto;
import com.zxplat.application.shared.pagination.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 药房退库单列表分页响应。
 */
@Getter
@Setter
public class PageReturnToWarehousesResponse extends PagedResult<ReturnToWarehouseListRowDto> {
}
