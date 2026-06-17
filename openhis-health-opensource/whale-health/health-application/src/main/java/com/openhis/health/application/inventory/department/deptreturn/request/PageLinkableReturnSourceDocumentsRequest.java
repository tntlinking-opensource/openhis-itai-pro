/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.department.deptreturn.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageLinkableReturnSourceDocumentsRequest extends AbstractHealthPageRequest {

    /** 源库位主键。 */
    private Long sourceLocationId;
}
