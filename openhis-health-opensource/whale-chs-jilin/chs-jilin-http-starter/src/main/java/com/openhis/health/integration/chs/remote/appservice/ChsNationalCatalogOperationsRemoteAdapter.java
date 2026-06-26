package com.openhis.health.integration.chs.remote.appservice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.openhis.chs.domain.catalog.dto.request.QueryNationalCatalogRequest;
import com.openhis.chs.domain.catalog.dto.request.Refresh1312Request;
import com.openhis.chs.domain.catalog.dto.request.StartCatalogDownloadRequest;
import com.openhis.chs.domain.catalog.dto.request.SuggestMedicationCatalogMappingRequest;
import com.openhis.chs.domain.catalog.dto.response.MedicationCatalogMappingSuggestDto;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadCursorDto;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadJobProgressDto;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadJobStartDto;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadPreviewDto;
import com.openhis.chs.domain.catalog.dto.response.NationalCatalogItemDto;
import com.openhis.chs.domain.catalog.facade.ChsNationalCatalogOperations;
import com.openhis.whale.core.application.dto.PagedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * remote 模式：HTTP 代理至 chs-application {@code ChsNationalCatalogAppService}。
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.chs.deployment.mode", havingValue = "remote")
public class ChsNationalCatalogOperationsRemoteAdapter implements ChsNationalCatalogOperations {

    private static final String SERVICE = "chsNationalCatalog";

    private final ChsRemoteAppServiceClient remoteClient;

    @Override
    public PagedResult<NationalCatalogItemDto> queryNationalCatalogPage(QueryNationalCatalogRequest request) {
        return remoteClient.post(
                SERVICE,
                "queryNationalCatalogPage",
                request,
                new TypeReference<PagedResult<NationalCatalogItemDto>>() {});
    }

    @Override
    public MedicationCatalogMappingSuggestDto suggestMedicationCatalogMapping(
            SuggestMedicationCatalogMappingRequest request) {
        return remoteClient.post(
            SERVICE,
            "suggestMedicationCatalogMapping",
            request,
            MedicationCatalogMappingSuggestDto.class);
    }

    @Override
    public int refreshFromPlatform1312(Refresh1312Request request) {
        Integer count = remoteClient.post(SERVICE, "refreshFromPlatform1312", request, Integer.class);
        if (count == null) {
            return 0;
        }
        return count;
    }

    @Override
    public List<CatalogDownloadCursorDto> listDownloadCursors(String contractBusNo) {
        return remoteClient.post(
                SERVICE,
                "listDownloadCursors",
                Map.of("value", contractBusNo),
                new TypeReference<List<CatalogDownloadCursorDto>>() {});
    }

    @Override
    public CatalogDownloadPreviewDto previewCatalogDownload(StartCatalogDownloadRequest request) {
        return remoteClient.post(SERVICE, "previewCatalogDownload", request, CatalogDownloadPreviewDto.class);
    }

    @Override
    public CatalogDownloadJobStartDto startCatalogDownload(StartCatalogDownloadRequest request) {
        return remoteClient.post(SERVICE, "startCatalogDownload", request, CatalogDownloadJobStartDto.class);
    }

    @Override
    public CatalogDownloadJobProgressDto getCatalogDownloadJobProgress(String jobId) {
        return remoteClient.post(
            SERVICE,
            "getCatalogDownloadJobProgress",
            Map.of("value", jobId),
            CatalogDownloadJobProgressDto.class);
    }
}
