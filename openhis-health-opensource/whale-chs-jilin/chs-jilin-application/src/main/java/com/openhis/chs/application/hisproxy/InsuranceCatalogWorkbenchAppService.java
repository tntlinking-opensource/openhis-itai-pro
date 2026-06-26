/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.hisproxy;

import com.openhis.chs.domain.catalog.dto.request.PagePendingCatalogMappingRequest;
import com.openhis.chs.domain.catalog.dto.response.PagePendingCatalogMappingResult;
import com.openhis.chs.application.hisproxy.support.PendingCatalogMappingLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 医保目录对码 Workbench（待上传列表；3301 批量由前端逐条调用 mapping 接口并展示进度）。
 *
 * <p>HTTP：{@code POST /api/app/insuranceCatalogWorkbench/{methodName}}。</p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsuranceCatalogWorkbenchAppService {

    private final PendingCatalogMappingLookup pendingCatalogMappingLookup;

    /**
     * 分页查询待上传/已对码/未选码的院内产品。
     *
     * @param request 分页与筛选条件，可为 null
     * @return 分页结果及各状态计数
     */
    public PagePendingCatalogMappingResult pagePendingMappings(PagePendingCatalogMappingRequest request) {
        // Gate：request 为空时使用默认分页参数
        PagePendingCatalogMappingRequest effectiveRequest;
        if (request == null) {
            effectiveRequest = new PagePendingCatalogMappingRequest();
        } else {
            effectiveRequest = request;
        }
        // Cmd：委托只读 UNION 查询
        return pendingCatalogMappingLookup.page(effectiveRequest);
    }
}
