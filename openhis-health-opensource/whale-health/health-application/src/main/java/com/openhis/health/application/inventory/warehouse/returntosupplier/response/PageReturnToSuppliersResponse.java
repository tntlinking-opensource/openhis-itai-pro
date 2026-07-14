/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returntosupplier.response;

import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.ReturnToSupplierListRowDto;
import com.openhis.health.application.shared.pagination.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 药库退供应商单列表分页响应（IF-WHS-RT-02，按 {@code bus_no} 聚合）。
 */
@Getter
@Setter
@Schema(description = "退供应商单列表分页 IF-WHS-RT-02")
public class PageReturnToSuppliersResponse extends PagedResult<ReturnToSupplierListRowDto> {
}
