/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.catalog.nationalcatalog.service;

import com.openhis.chs.application.catalog.nationalcatalog.mapper.InsuranceNationalCatalogMapper;
import com.openhis.chs.domain.catalog.access.Cat1312Invoker;
import com.openhis.chs.application.catalog.nationalcatalog.request.QueryNationalCatalogRequest;
import com.openhis.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.openhis.chs.domain.catalog.command.Refresh1312Command;
import com.openhis.chs.domain.catalog.entity.YbNationalCatalogItem;
import com.openhis.chs.domain.catalog.queries.NationalCatalogPageQuery;
import com.openhis.chs.domain.catalog.result.NationalCatalogItemResult;
import com.openhis.chs.domain.catalog.service.YbNationalCatalogQueryService;
import com.openhis.chs.domain.contract.ChsContractSnapshot;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 国家目录管理页 UI 应用服务（POST /api/app/insuranceNationalCatalog/*）。
 * <p>职责：本地镜像分页查询、1312 实时刷新。</p>
 */
@Service
@RequiredArgsConstructor
public class InsuranceNationalCatalogAppService {

    private final YbNationalCatalogQueryService nationalCatalogQueryService;
    private final ChsInsuranceContractReader contractReader;
    private final Cat1312Invoker cat1312Invoker;

    /**
     * 分页查询本地国家医保目录镜像。
     */
    public PagedResult<NationalCatalogItemResult> queryNationalCatalogPage(QueryNationalCatalogRequest request) {
        QueryNationalCatalogRequest safe = request == null ? new QueryNationalCatalogRequest() : request;
        ChsContractSnapshot contract = contractReader.requireContract(safe.getContractBusNo(), safe.getContractId());
        NationalCatalogPageQuery query = InsuranceNationalCatalogMapper.toPageQuery(
            safe, contract.id(), TenantUtils.getCurrentTenantId());
        var page = nationalCatalogQueryService.page(query);
        List<NationalCatalogItemResult> rows = new ArrayList<>(page.getContent().size());
        for (YbNationalCatalogItem item : page.getContent()) {
            rows.add(InsuranceNationalCatalogMapper.toItemResult(item));
        }
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
