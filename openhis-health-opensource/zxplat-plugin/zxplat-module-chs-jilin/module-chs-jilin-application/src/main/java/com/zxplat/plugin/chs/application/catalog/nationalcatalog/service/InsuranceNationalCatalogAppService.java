/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.catalog.nationalcatalog.service;

import com.zxplat.plugin.chs.application.catalog.nationalcatalog.mapper.InsuranceNationalCatalogMapper;
import com.zxplat.plugin.chs.domain.catalog.access.Cat1312Invoker;
import com.zxplat.plugin.chs.application.catalog.nationalcatalog.request.QueryNationalCatalogRequest;
import com.zxplat.plugin.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.zxplat.plugin.chs.domain.catalog.command.Refresh1312Command;
import com.zxplat.plugin.chs.domain.catalog.entity.YbNationalCatalogItem;
import com.zxplat.plugin.chs.domain.catalog.queries.NationalCatalogPageQuery;
import com.zxplat.plugin.chs.domain.catalog.result.NationalCatalogItemResult;
import com.zxplat.plugin.chs.domain.catalog.service.YbNationalCatalogQueryService;
import com.zxplat.plugin.chs.domain.contract.ChsContractSnapshot;
import com.zxplat.plugin.chs.domain.notice.queryservice.NationalCatalogOverlayQueryService;
import com.zxplat.framework.core.application.dto.PagedResult;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 国家目录管理页 UI 应用服务（POST /api/app/insuranceNationalCatalog/*）。
 * <p>职责：本地镜像分页查询（合成局文 overlay 选码可用）、1312 实时刷新。</p>
 */
@Service
@RequiredArgsConstructor
public class InsuranceNationalCatalogAppService {

    private final YbNationalCatalogQueryService nationalCatalogQueryService;
    private final NationalCatalogOverlayQueryService overlayQueryService;
    private final ChsInsuranceContractReader contractReader;
    private final Cat1312Invoker cat1312Invoker;

    /**
     * 分页查询本地国家医保目录镜像（镜像 ⋈ overlay）。
     */
    public PagedResult<NationalCatalogItemResult> queryNationalCatalogPage(QueryNationalCatalogRequest request) {
        QueryNationalCatalogRequest safe = request == null ? new QueryNationalCatalogRequest() : request;
        ChsContractSnapshot contract = contractReader.requireContract(safe.getContractBusNo(), safe.getContractId());
        Long tenantId = TenantUtils.getCurrentTenantId();
        NationalCatalogPageQuery query = InsuranceNationalCatalogMapper.toPageQuery(
            safe, contract.id(), tenantId);
        var page = nationalCatalogQueryService.page(query);
        List<NationalCatalogItemResult> rows = new ArrayList<>(page.getContent().size());
        for (YbNationalCatalogItem item : page.getContent()) {
            rows.add(InsuranceNationalCatalogMapper.toItemResult(item));
        }
        overlayQueryService.decorateSelectable(
            tenantId, contract.id(), safe.getListType(), rows, LocalDate.now());
        return PagedResult.of(rows, page.getTotalElements(), query.springPageIndex() + 1, query.getBoundedPageSize());
    }

    /**
     * 调用 1312 接口实时刷新国家目录并 upsert 至本地镜像。
     *
     * @return 本次 upsert 行数
     */
    public int refreshFromPlatform1312(Refresh1312Command request) {
        Refresh1312Command safe = request == null ? new Refresh1312Command() : request;
        return cat1312Invoker.invoke(safe);
    }
}
