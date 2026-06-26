package com.openhis.health.integration.chs.remote.appservice;

import com.openhis.chs.domain.platformdict.dto.request.LookupPlatformDictLabelsRequest;
import com.openhis.chs.domain.platformdict.dto.request.QueryPlatformDictPageRequest;
import com.openhis.chs.domain.platformdict.dto.request.SyncPlatformDictFrom1901Request;
import com.openhis.chs.domain.platformdict.dto.response.PlatformDictItemDto;
import com.openhis.chs.domain.platformdict.dto.response.PlatformDictLabelsDto;
import com.openhis.chs.domain.platformdict.dto.response.PlatformDictSyncLogDto;
import com.openhis.chs.domain.platformdict.dto.response.PlatformDictSyncResultDto;
import com.openhis.chs.domain.platformdict.facade.ChsPlatformDictOperations;
import com.openhis.whale.core.application.dto.PagedResult;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;

/** remote 模式：HTTP 代理至 chs-application {@code ChsPlatformDictAppService}。 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.chs.deployment.mode", havingValue = "remote")
public class ChsPlatformDictOperationsRemoteAdapter implements ChsPlatformDictOperations {

    private static final String SERVICE = "chsPlatformDict";

    private final ChsRemoteAppServiceClient remoteClient;

    @Override
    public PlatformDictSyncResultDto syncFrom1901(SyncPlatformDictFrom1901Request request) {
        return remoteClient.post(SERVICE, "syncFrom1901", request, PlatformDictSyncResultDto.class);
    }

    @Override
    public PlatformDictLabelsDto lookupDictLabels(LookupPlatformDictLabelsRequest request) {
        return remoteClient.post(SERVICE, "lookupDictLabels", request, PlatformDictLabelsDto.class);
    }

    @Override
    public PlatformDictSyncLogDto getLastSyncLog(String dictType) {
        return remoteClient.post(
            SERVICE,
            "getLastSyncLog",
            Map.of("dictType", dictType != null ? dictType : ""),
            PlatformDictSyncLogDto.class);
    }

    @Override
    public PagedResult<PlatformDictItemDto> queryPlatformDictPage(QueryPlatformDictPageRequest request) {
        return remoteClient.post(
            SERVICE,
            "queryPlatformDictPage",
            request,
            new TypeReference<PagedResult<PlatformDictItemDto>>() {});
    }
}
