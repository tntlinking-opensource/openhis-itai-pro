/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.foundation.platformdict.service;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.application.foundation.platformdict.mapper.InsurancePlatformDictAppMapper;
import com.openhis.chs.application.foundation.platformdict.request.GetPlatformDictSyncLogRequest;
import com.openhis.chs.application.foundation.platformdict.request.LookupPlatformDictLabelsRequest;
import com.openhis.chs.domain.share.constants.PlatformDictConstants;
import com.openhis.chs.domain.platformdict.access.PlatformDictSyncAccess;
import com.openhis.chs.domain.platformdict.command.QueryPlatformDictPageCommand;
import com.openhis.chs.domain.platformdict.command.SyncPlatformDictFrom1901Command;
import com.openhis.chs.domain.platformdict.entity.YbPlatformDictItem;
import com.openhis.chs.domain.platformdict.result.PlatformDictItemResult;
import com.openhis.chs.domain.platformdict.result.PlatformDictLabelsResult;
import com.openhis.chs.domain.platformdict.result.PlatformDictSyncLogResult;
import com.openhis.chs.domain.platformdict.result.PlatformDictSyncResult;
import com.openhis.chs.domain.platformdict.service.YbPlatformDictLookupService;
import com.openhis.chs.domain.platformdict.service.YbPlatformDictPageQueryService;
import com.openhis.chs.domain.platformdict.service.YbPlatformDictSyncLogQueryService;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 1901 平台字典 UI 应用服务（POST /api/app/insurancePlatformDict/*）。
 * <p>职责：字典同步、标签查询、同步日志与本地镜像分页。</p>
 */
@Service
@RequiredArgsConstructor
public class InsurancePlatformDictAppService {

    private final PlatformDictSyncAccess platformDictSyncAccess;
    private final YbPlatformDictLookupService platformDictLookupService;
    private final YbPlatformDictPageQueryService pageQueryService;
    private final YbPlatformDictSyncLogQueryService syncLogQueryService;

    /**
     * 调用 1901 接口同步平台字典至本地镜像。
     */
    public PlatformDictSyncResult syncFrom1901(SyncPlatformDictFrom1901Command request) {
        return platformDictSyncAccess.sync(request);
    }

    /**
     * 按字典类型与编码批量查询显示标签。
     */
    public PlatformDictLabelsResult lookupDictLabels(LookupPlatformDictLabelsRequest request) {
        LookupPlatformDictLabelsRequest safe = request == null ? new LookupPlatformDictLabelsRequest() : request;
        String dictType = resolveDictType(safe.getDictType());
        List<String> values = safe.getDictValues() != null ? safe.getDictValues() : List.of();
        Map<String, String> labels = platformDictLookupService.lookupLabels(
            TenantUtils.getCurrentTenantId(), dictType, values);
        return new PlatformDictLabelsResult()
            .setDictType(dictType)
            .setLabelsByValue(labels);
    }

    /**
     * 查询指定字典类型最近一次同步日志。
     */
    public PlatformDictSyncLogResult getLastSyncLog(GetPlatformDictSyncLogRequest request) {
        String dictType = resolveDictType(request != null ? request.getDictType() : null);
        Long tenantId = TenantUtils.getCurrentTenantId();
        return syncLogQueryService.findLatestByDictType(tenantId, dictType)
            .map(InsurancePlatformDictAppMapper::toSyncLogResult)
            .orElse(null);
    }

    /**
     * 分页查询本地平台字典镜像。
     */
    public PagedResult<PlatformDictItemResult> queryPlatformDictPage(QueryPlatformDictPageCommand request) {
        if (request == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "查询请求不能为空");
        }
        String dictType = resolveDictType(request.getDictType());
        int pageNo = request.getPageNo() != null && request.getPageNo() > 0 ? request.getPageNo() : 1;
        int pageSize = request.getPageSize() != null && request.getPageSize() > 0 ? request.getPageSize() : 50;
        Long tenantId = TenantUtils.getCurrentTenantId();
        var page = pageQueryService.page(tenantId, dictType, request.getKeyword(), pageNo, pageSize);
        List<PlatformDictItemResult> rows = new ArrayList<>(page.getContent().size());
        for (YbPlatformDictItem item : page.getContent()) {
            rows.add(InsurancePlatformDictAppMapper.toItemResult(item));
        }
        PagedResult<PlatformDictItemResult> result = new PagedResult<>();
        result.setContent(rows);
        result.setTotalCount(page.getTotalElements());
        result.setPageNo(page.getNumber() + 1);
        result.setPageSize(page.getSize());
        return result;
    }

    private static String resolveDictType(String dictType) {
        if (isNotEmpty(dictType)) {
            return dictType.trim();
        }
        return PlatformDictConstants.DEFAULT_ADMDVS_DICT_TYPE;
    }
}
