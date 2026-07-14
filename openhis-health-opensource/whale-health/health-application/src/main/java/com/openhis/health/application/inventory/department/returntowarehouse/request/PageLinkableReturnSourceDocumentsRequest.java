/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.returntowarehouse.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 可关联科室入库单分页查询条件（IF-DEPT-RTW-12）。
 */
@Getter
@Setter
public class PageLinkableReturnSourceDocumentsRequest extends AbstractHealthPageRequest {

    /** 源库位主键。 */
    private Long sourceLocationId;
}
