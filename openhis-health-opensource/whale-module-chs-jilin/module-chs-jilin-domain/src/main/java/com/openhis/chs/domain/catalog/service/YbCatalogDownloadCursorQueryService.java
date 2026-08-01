/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.service;

import com.openhis.chs.domain.share.constants.ChsCatalogConstants;
import com.openhis.chs.domain.catalog.entity.YbCatalogDownloadCursor;
import com.openhis.chs.domain.catalog.repository.YbCatalogDownloadCursorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 医保目录下载游标只读查询
 */
@Service
@RequiredArgsConstructor
public class YbCatalogDownloadCursorQueryService {

    private final YbCatalogDownloadCursorRepository downloadCursorRepository;

    /**
     * 列出契约下各 infno 的近期游标快照。
     * <p>按 {@code last_success_at} 倒序后，<b>每个 infno 各保留</b>
     * {@link ChsCatalogConstants#CATALOG_DOWNLOAD_CURSOR_HISTORY_PER_INFNO} 条，
     * 避免全局 LIMIT 把 1301 等较早同步完的接口整段截掉。</p>
     *
     * @param contractId 契约主键
     * @param tenantId   租户
     * @return 游标列表，参数无效时空列
     */
    public List<YbCatalogDownloadCursor> listByContractIdAndTenantId(Long contractId, Long tenantId) {
        if (contractId == null || tenantId == null) {
            return Collections.emptyList();
        }
        List<YbCatalogDownloadCursor> all =
            downloadCursorRepository.findByContractIdAndTenantIdAndIsDeletedFalseOrderByLastSuccessAtDescIdDesc(
                contractId, tenantId);
        int perInfno = ChsCatalogConstants.CATALOG_DOWNLOAD_CURSOR_HISTORY_PER_INFNO;
        Map<String, Integer> taken = new HashMap<>();
        List<YbCatalogDownloadCursor> result = new ArrayList<>();
        for (YbCatalogDownloadCursor row : all) {
            if (row == null) {
                continue;
            }
            String infno = row.getInfno() == null ? "" : row.getInfno();
            int n = taken.getOrDefault(infno, 0);
            if (n >= perInfno) {
                continue;
            }
            result.add(row);
            taken.put(infno, n + 1);
        }
        return result;
    }
}
