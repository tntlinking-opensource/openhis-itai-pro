/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.catalog.workbench.service;

import com.openhis.chs.domain.catalog.command.PagePendingCatalogMappingCommand;
import com.openhis.chs.domain.catalog.repository.PendingCatalogMappingLookup;
import com.openhis.chs.domain.catalog.result.PagePendingCatalogMappingResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 医保目录对码 Workbench 页（POST /api/app/insuranceCatalogWorkbench/*）。
 * <p>职责：待上传/待对码产品分页列表。</p>
 */
@Service
@RequiredArgsConstructor
public class InsuranceCatalogWorkbenchAppService {

    private final PendingCatalogMappingLookup pendingCatalogMappingLookup;

    /** 分页查询待上传对码记录。 */
    public PagePendingCatalogMappingResult pagePendingMappings(PagePendingCatalogMappingCommand command) {
        return pendingCatalogMappingLookup.page(
            command == null ? new PagePendingCatalogMappingCommand() : command);
    }
}
