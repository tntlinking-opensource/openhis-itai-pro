/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.purchaserequest.response;

import com.openhis.health.application.inventory.department.purchaserequest.dto.DeptPurchaseRequestListRowDto;
import com.openhis.health.application.shared.pagination.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 科室采购申请单列表分页响应（IF-DEP-CPR-02；按 {@code busNo} 聚合行，B.10.11）。
 */
@Getter
@Setter
public class PageDeptPurchaseRequestsResponse extends PagedResult<DeptPurchaseRequestListRowDto> {
}
