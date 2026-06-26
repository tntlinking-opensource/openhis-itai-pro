/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.catalog.service;

import com.openhis.chs.domain.catalog.ChsCatalogConstants;
import com.openhis.chs.domain.catalog.entity.YbCatalogDownloadCursor;
import com.openhis.chs.domain.catalog.repository.YbCatalogDownloadCursorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 医保目录下载游标只读查询。
 */
@Service
@RequiredArgsConstructor
public class YbCatalogDownloadCursorQueryService {

    private final YbCatalogDownloadCursorRepository downloadCursorRepository;

    /**
     * 列出契约下最近的目录下载游标快照（条数受 {@link ChsCatalogConstants#CATALOG_DOWNLOAD_CURSOR_HISTORY_LIMIT} 限制）。
     *
     * @param contractId 契约主键
     * @param tenantId   租户
     * @return 游标列表，参数无效时空列表
     */
    public List<YbCatalogDownloadCursor> listByContractIdAndTenantId(Long contractId, Long tenantId) {
        if (contractId == null || tenantId == null) {
            return Collections.emptyList();
        }
        int limit = ChsCatalogConstants.CATALOG_DOWNLOAD_CURSOR_HISTORY_LIMIT;
        return downloadCursorRepository.findByContractIdAndTenantIdAndIsDeletedFalseOrderByLastSuccessAtDescIdDesc(
            contractId,
            tenantId,
            PageRequest.of(0, limit));
    }
}
