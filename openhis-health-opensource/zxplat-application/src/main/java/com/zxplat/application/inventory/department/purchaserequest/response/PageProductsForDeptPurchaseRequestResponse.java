/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.purchaserequest.response;

import com.zxplat.application.inventory.department.purchaserequest.dto.ProductDefinitionItemDto;
import com.zxplat.application.shared.pagination.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 科室采购申请选品分页响应（IF-DEP-CPR-11；B.10.11）。
 */
@Getter
@Setter
public class PageProductsForDeptPurchaseRequestResponse extends PagedResult<ProductDefinitionItemDto> {
}
