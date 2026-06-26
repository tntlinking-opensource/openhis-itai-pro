package com.openhis.health.integration.chs.remote.appservice;

import com.openhis.chs.domain.catalog.dto.request.RevokeCatalog3302Request;
import com.openhis.chs.domain.catalog.dto.request.UploadCatalog3301Request;
import com.openhis.chs.domain.catalog.dto.response.Catalog3301UploadResultDto;
import com.openhis.chs.domain.catalog.dto.response.Catalog3302RevokeResultDto;
import com.openhis.chs.domain.catalog.facade.ChsCatalogMappingOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * remote 模式：HTTP 代理至 chs-application {@code ChsCatalogMappingAppService}。
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.chs.deployment.mode", havingValue = "remote")
public class ChsCatalogMappingOperationsRemoteAdapter implements ChsCatalogMappingOperations {

    private static final String SERVICE = "chsCatalogMapping";

    private final ChsRemoteAppServiceClient remoteClient;

    @Override
    public Catalog3301UploadResultDto uploadCatalog3301(UploadCatalog3301Request request) {
        return remoteClient.post(SERVICE, "uploadCatalog3301", request, Catalog3301UploadResultDto.class);
    }

    @Override
    public Catalog3302RevokeResultDto revokeCatalog3302(RevokeCatalog3302Request request) {
        return remoteClient.post(SERVICE, "revokeCatalog3302", request, Catalog3302RevokeResultDto.class);
    }
}
