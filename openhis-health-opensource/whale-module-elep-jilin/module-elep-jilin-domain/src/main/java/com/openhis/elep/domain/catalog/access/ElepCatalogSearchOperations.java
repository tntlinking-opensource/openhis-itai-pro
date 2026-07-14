/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.catalog.access;

import com.openhis.elep.domain.catalog.command.ElepCatalogCodeRequest;
import com.openhis.elep.domain.catalog.model.ElepCatalogInvalidLine;
import com.openhis.elep.domain.catalog.model.ElepCatalogItem;
import com.openhis.elep.domain.catalog.model.ElepCatalogPageResult;
import com.openhis.elep.domain.catalog.command.ElepCatalogSearchRequest;
import com.openhis.elep.domain.catalog.command.ElepCatalogValidateCommand;

import java.util.List;
import java.util.Optional;

/** 电子处方目录搜索端口（由 application {@code ElepCatalogSearchAppService} 实现）。 */
public interface ElepCatalogSearchOperations {

    ElepCatalogPageResult searchDrug(ElepCatalogSearchRequest request);

    ElepCatalogPageResult searchDiagnosis(ElepCatalogSearchRequest request);

    Optional<ElepCatalogItem> getDrugByCode(ElepCatalogCodeRequest request);

    Optional<ElepCatalogItem> getDiagnosisByCode(ElepCatalogCodeRequest request);

    List<ElepCatalogInvalidLine> validateLines(ElepCatalogValidateCommand command);
}
