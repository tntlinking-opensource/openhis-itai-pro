/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.elep.remote.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.openhis.elep.domain.catalog.command.ElepCatalogCodeRequest;
import com.openhis.elep.domain.catalog.model.ElepCatalogInvalidLine;
import com.openhis.elep.domain.catalog.model.ElepCatalogItem;
import com.openhis.elep.domain.catalog.model.ElepCatalogPageResult;
import com.openhis.elep.domain.catalog.command.ElepCatalogSearchRequest;
import com.openhis.elep.domain.catalog.command.ElepCatalogValidateCommand;
import com.openhis.elep.domain.catalog.access.ElepCatalogSearchOperations;
import com.openhis.health.integration.elep.remote.appservice.ElepRemoteAppServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * remote 模式：HTTP 代理至 ELEP 服务 {@code ElepCatalogSearchAppService}。
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.elep.deployment.mode", havingValue = "remote")
public class ElepCatalogSearchOperationsRemoteClientImpl implements ElepCatalogSearchOperations {

    private static final String SERVICE = "elepCatalogSearch";

    private final ElepRemoteAppServiceClient remoteClient;

    @Override
    public ElepCatalogPageResult searchDrug(ElepCatalogSearchRequest request) {
        return remoteClient.post(SERVICE, "searchDrug", request, ElepCatalogPageResult.class);
    }

    @Override
    public ElepCatalogPageResult searchDiagnosis(ElepCatalogSearchRequest request) {
        return remoteClient.post(SERVICE, "searchDiagnosis", request, ElepCatalogPageResult.class);
    }

    @Override
    public Optional<ElepCatalogItem> getDrugByCode(ElepCatalogCodeRequest request) {
        return remoteClient.postOptional(SERVICE, "getDrugByCode", request, ElepCatalogItem.class);
    }

    @Override
    public Optional<ElepCatalogItem> getDiagnosisByCode(ElepCatalogCodeRequest request) {
        return remoteClient.postOptional(SERVICE, "getDiagnosisByCode", request, ElepCatalogItem.class);
    }

    @Override
    public List<ElepCatalogInvalidLine> validateLines(ElepCatalogValidateCommand command) {
        return remoteClient.post(
                SERVICE,
                "validateLines",
                command,
                new TypeReference<List<ElepCatalogInvalidLine>>() {});
    }
}
