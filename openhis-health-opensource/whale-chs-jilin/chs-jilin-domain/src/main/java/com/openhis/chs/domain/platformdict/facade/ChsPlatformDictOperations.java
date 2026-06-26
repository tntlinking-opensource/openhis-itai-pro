package com.openhis.chs.domain.platformdict.facade;

import com.openhis.chs.domain.platformdict.dto.request.LookupPlatformDictLabelsRequest;
import com.openhis.chs.domain.platformdict.dto.request.QueryPlatformDictPageRequest;
import com.openhis.chs.domain.platformdict.dto.request.SyncPlatformDictFrom1901Request;
import com.openhis.chs.domain.platformdict.dto.response.PlatformDictItemDto;
import com.openhis.chs.domain.platformdict.dto.response.PlatformDictLabelsDto;
import com.openhis.chs.domain.platformdict.dto.response.PlatformDictSyncLogDto;
import com.openhis.chs.domain.platformdict.dto.response.PlatformDictSyncResultDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * 1901 平台字典应用能力（chs-application 实现；bridge 1A 代理）。
 */
public interface ChsPlatformDictOperations {

    PlatformDictSyncResultDto syncFrom1901(SyncPlatformDictFrom1901Request request);

    PlatformDictLabelsDto lookupDictLabels(LookupPlatformDictLabelsRequest request);

    PlatformDictSyncLogDto getLastSyncLog(String dictType);

    PagedResult<PlatformDictItemDto> queryPlatformDictPage(QueryPlatformDictPageRequest request);
}
