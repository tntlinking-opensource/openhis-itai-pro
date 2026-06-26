/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.hisproxy;

import com.openhis.chs.domain.platformdict.dto.request.LookupPlatformDictLabelsRequest;
import com.openhis.chs.domain.platformdict.dto.request.QueryPlatformDictPageRequest;
import com.openhis.chs.domain.platformdict.dto.request.SyncPlatformDictFrom1901Request;
import com.openhis.chs.domain.platformdict.dto.response.PlatformDictItemDto;
import com.openhis.chs.domain.platformdict.dto.response.PlatformDictLabelsDto;
import com.openhis.chs.domain.platformdict.dto.response.PlatformDictSyncLogDto;
import com.openhis.chs.domain.platformdict.dto.response.PlatformDictSyncResultDto;
import com.openhis.chs.domain.platformdict.facade.ChsPlatformDictOperations;
import com.openhis.whale.core.application.dto.PagedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 1901 平台字典 bridge 代理（方案 1A：{@code /api/app/insurancePlatformDict/*}）。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsurancePlatformDictAppService {

    private final ChsPlatformDictOperations platformDictOperations;

    public PlatformDictSyncResultDto syncFrom1901(SyncPlatformDictFrom1901Request request) {
        return platformDictOperations.syncFrom1901(request);
    }

    public PlatformDictLabelsDto lookupDictLabels(LookupPlatformDictLabelsRequest request) {
        return platformDictOperations.lookupDictLabels(request);
    }

    public PlatformDictSyncLogDto getLastSyncLog(String dictType) {
        return platformDictOperations.getLastSyncLog(dictType);
    }

    public PagedResult<PlatformDictItemDto> queryPlatformDictPage(QueryPlatformDictPageRequest request) {
        return platformDictOperations.queryPlatformDictPage(request);
    }
}
