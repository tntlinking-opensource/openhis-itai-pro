/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.repository;

import com.openhis.chs.domain.catalog.command.PagePendingCatalogMappingCommand;
import com.openhis.chs.domain.catalog.result.PagePendingCatalogMappingResult;

/** 医保目录对码 Workbench 只读查询（UNION 三主表） */
public interface PendingCatalogMappingLookup {

    PagePendingCatalogMappingResult page(PagePendingCatalogMappingCommand command);
}
