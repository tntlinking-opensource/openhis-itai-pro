/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.purchaserequest.response;

import com.openhis.health.application.inventory.department.purchaserequest.dto.ProductDefinitionItemDto;
import com.openhis.health.application.shared.pagination.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 科室采购申请选品分页响应（IF-DEP-CPR-11；B.10.11）。
 */
@Getter
@Setter
public class PageProductsForDeptPurchaseRequestResponse extends PagedResult<ProductDefinitionItemDto> {
}
