package com.openhis.health.integration.elep.remote.appservice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.openhis.elep.domain.catalog.dto.ElepCatalogCodeRequest;
import com.openhis.elep.domain.catalog.dto.ElepCatalogInvalidLineDto;
import com.openhis.elep.domain.catalog.dto.ElepCatalogItemDto;
import com.openhis.elep.domain.catalog.dto.ElepCatalogPageResult;
import com.openhis.elep.domain.catalog.dto.ElepCatalogSearchRequest;
import com.openhis.elep.domain.catalog.dto.ElepCatalogValidateCommand;
import com.openhis.elep.domain.catalog.facade.ElepCatalogSearchOperations;
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
public class ElepCatalogSearchOperationsRemoteAdapter implements ElepCatalogSearchOperations {

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
    public Optional<ElepCatalogItemDto> getDrugByCode(ElepCatalogCodeRequest request) {
        return remoteClient.postOptional(SERVICE, "getDrugByCode", request, ElepCatalogItemDto.class);
    }

    @Override
    public Optional<ElepCatalogItemDto> getDiagnosisByCode(ElepCatalogCodeRequest request) {
        return remoteClient.postOptional(SERVICE, "getDiagnosisByCode", request, ElepCatalogItemDto.class);
    }

    @Override
    public List<ElepCatalogInvalidLineDto> validateLines(ElepCatalogValidateCommand command) {
        return remoteClient.post(
                SERVICE,
                "validateLines",
                command,
                new TypeReference<List<ElepCatalogInvalidLineDto>>() {});
    }
}
