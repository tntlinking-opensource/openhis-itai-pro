/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.catalog.facade;

import com.openhis.elep.domain.catalog.dto.ElepCatalogCodeRequest;
import com.openhis.elep.domain.catalog.dto.ElepCatalogInvalidLineDto;
import com.openhis.elep.domain.catalog.dto.ElepCatalogItemDto;
import com.openhis.elep.domain.catalog.dto.ElepCatalogPageResult;
import com.openhis.elep.domain.catalog.dto.ElepCatalogSearchRequest;
import com.openhis.elep.domain.catalog.dto.ElepCatalogValidateCommand;

import java.util.List;
import java.util.Optional;

/** 电子处方目录搜索门面（由 application 实现）。 */
public interface ElepCatalogSearchOperations {

    ElepCatalogPageResult searchDrug(ElepCatalogSearchRequest request);

    ElepCatalogPageResult searchDiagnosis(ElepCatalogSearchRequest request);

    Optional<ElepCatalogItemDto> getDrugByCode(ElepCatalogCodeRequest request);

    Optional<ElepCatalogItemDto> getDiagnosisByCode(ElepCatalogCodeRequest request);

    List<ElepCatalogInvalidLineDto> validateLines(ElepCatalogValidateCommand command);
}
