/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.catalog.download.service;

import com.openhis.chs.application.catalog.download.mapper.InsuranceCatalogDownloadMapper;
import com.openhis.chs.domain.catalog.access.CatalogDownloadAccess;
import com.openhis.chs.application.catalog.download.request.GetCatalogDownloadJobProgressRequest;
import com.openhis.chs.application.catalog.download.request.ListDownloadCursorsRequest;
import com.openhis.chs.application.catalog.download.response.ListCatalogDownloadCursorsResponse;
import com.openhis.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.openhis.chs.domain.catalog.command.StartCatalogDownloadCommand;
import com.openhis.chs.domain.catalog.entity.YbCatalogDownloadCursor;
import com.openhis.chs.domain.catalog.result.CatalogDownloadJobProgressResult;
import com.openhis.chs.domain.catalog.result.CatalogDownloadJobStartResult;
import com.openhis.chs.domain.catalog.result.CatalogDownloadPreviewResult;
import com.openhis.chs.domain.catalog.service.YbCatalogDownloadCursorQueryService;
import com.openhis.chs.domain.catalog.service.YbNationalCatalogQueryService;
import com.openhis.chs.domain.contract.ChsContractSnapshot;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 目录文件下载页 UI 应用服务（POST /api/app/insuranceCatalogDownload/*）。
 * <p>职责：1301/1305 等文件型目录探测、异步入库、游标查询。</p>
 */
@Service
@RequiredArgsConstructor
public class InsuranceCatalogDownloadAppService {

    private final YbCatalogDownloadCursorQueryService catalogDownloadCursorQueryService;
    private final YbNationalCatalogQueryService nationalCatalogQueryService;
    private final ChsInsuranceContractReader contractReader;
    private final CatalogDownloadAccess catalogDownloadAccess;

    /** 探测下载：拉 ZIP 预览，不提交入库任务。 */
    public CatalogDownloadPreviewResult previewCatalogDownload(StartCatalogDownloadCommand request) {
        StartCatalogDownloadCommand safe = request == null ? new StartCatalogDownloadCommand() : request;
        return catalogDownloadAccess.previewCatalogDownload(safe);
    }

    /** 启动下载：阶段 1 拉 ZIP 后提交异步入库。 */
    public CatalogDownloadJobStartResult startCatalogDownload(StartCatalogDownloadCommand request) {
        StartCatalogDownloadCommand safe = request == null ? new StartCatalogDownloadCommand() : request;
        return catalogDownloadAccess.startCatalogDownload(safe);
    }

    /** 查询异步入库任务进度。 */
    public CatalogDownloadJobProgressResult getCatalogDownloadJobProgress(
            GetCatalogDownloadJobProgressRequest request) {
        GetCatalogDownloadJobProgressRequest safe =
            request == null ? new GetCatalogDownloadJobProgressRequest() : request;
        return catalogDownloadAccess.getCatalogDownloadJobProgress(safe.getJobId());
    }

    /** 查询各 infno 的增量下载游标（含镜像行数等展示字段）。 */
    public ListCatalogDownloadCursorsResponse listDownloadCursors(ListDownloadCursorsRequest request) {
        ListDownloadCursorsRequest safe = request == null ? new ListDownloadCursorsRequest() : request;
        ChsContractSnapshot contract = contractReader.requireContract(safe.getContractBusNo(), null);
        Long tenantId = TenantUtils.getCurrentTenantId();
        List<YbCatalogDownloadCursor> rows = catalogDownloadCursorQueryService.listByContractIdAndTenantId(
            contract.id(), tenantId);

        Set<String> listTypes = new HashSet<>();
        for (YbCatalogDownloadCursor row : rows) {
            if (row == null || row.getInfno() == null) {
                continue;
            }
            listTypes.add(InsuranceCatalogDownloadMapper.listTypeForInfno(row.getInfno()));
        }
        Map<String, Long> listTypeRowCounts = new HashMap<>();
        for (String listType : listTypes) {
            listTypeRowCounts.put(
                listType,
                nationalCatalogQueryService.countByContractAndListType(contract.id(), listType, tenantId));
        }
        return InsuranceCatalogDownloadMapper.toCursorResponse(rows, listTypeRowCounts);
    }
}
