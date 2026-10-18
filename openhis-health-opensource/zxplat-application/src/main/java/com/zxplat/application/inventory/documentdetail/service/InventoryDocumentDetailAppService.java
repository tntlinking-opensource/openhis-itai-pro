/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.documentdetail.service;

import com.zxplat.application.inventory.documentdetail.request.ExportDocumentDetailsRequest;
import com.zxplat.application.inventory.documentdetail.request.LoadDocumentDetailFormInitRequest;
import com.zxplat.application.inventory.documentdetail.request.PageDocumentDetailsRequest;
import com.zxplat.application.inventory.documentdetail.request.SummarizeDocumentDetailsRequest;
import com.zxplat.application.inventory.documentdetail.response.ExportDocumentDetailsResponse;
import com.zxplat.application.inventory.documentdetail.response.LoadDocumentDetailFormInitResponse;
import com.zxplat.application.inventory.documentdetail.response.PageDocumentDetailsResponse;
import com.zxplat.application.inventory.documentdetail.response.SummarizeDocumentDetailsResponse;
import com.zxplat.application.inventory.documentdetail.support.DocumentDetailDomainScope;
import com.zxplat.application.inventory.documentdetail.support.DocumentDetailReadLoader;
import com.zxplat.framework.core.application.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 进销存单据详情报表编排（AS-INV-RPT-002 · IF-INV-DDC-01～04）。
 *
 * <p>职责边界：只读分页/合计（含按品规汇总行）/导出；药库/药房/科室由请求 {@code domainScope} 区分；
 * 默认 {@code AGREE}，期初显式类型可含 {@code APPROVAL}（BR-INV-DDC-003）；库房仅进价、无售价。</p>
 * <ul>
 *   <li>{@link DocumentDetailReadLoader}：租户 Gate、Query 装配、enrich、换算与 Excel</li>
 * </ul>
 * <p>接入：AppServiceControllerRegistrar → POST /api/app/inventoryDocumentDetail/{methodName}。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryDocumentDetailAppService {

    /** 读侧编排（分页/合计/导出/init 主路径）。 */
    private final DocumentDetailReadLoader documentDetailReadLoader;

    /**
     * 页面初始化（IF-INV-DDC-01）。
     *
     * @param request 含 {@code domainScope}
     * @return init 响应
     * @throws BusinessException {@code INV_DDC_201}、{@code INV_DDC_202}
     */
    public LoadDocumentDetailFormInitResponse loadDocumentDetailFormInit(
        @Valid LoadDocumentDetailFormInitRequest request
    ) {
        // Gate：业务域须为 WAREHOUSE/PHARMACY/DEPARTMENT 之一
        DocumentDetailDomainScope scope = DocumentDetailDomainScope.requireFromCode(request.getDomainScope());
        // Asm：按域返回类型白名单、库位下拉与默认审批月
        return this.documentDetailReadLoader.loadDocumentDetailFormInit(scope);
    }

    /**
     * 明细分页（IF-INV-DDC-02）。
     *
     * @param request 分页、筛选与 {@code domainScope}
     * @return 分页响应
     * @throws BusinessException {@code INV_DDC_202}～{@code INV_DDC_204}、{@code INV_DDC_207}
     */
    public PageDocumentDetailsResponse pageDocumentDetails(@Valid PageDocumentDetailsRequest request) {
        // Gate：业务域须合法（与 typeEnum 白名单维度一致）
        DocumentDetailDomainScope scope = DocumentDetailDomainScope.requireFromCode(request.getDomainScope());
        // Pre/Asm：筛选、领域分页、enrich 与操作单位换算
        return this.documentDetailReadLoader.pageDocumentDetails(scope, request);
    }

    /**
     * 筛选全量合计与按品规汇总行（IF-INV-DDC-03）。
     *
     * @param request 筛选与 {@code domainScope}
     * @return 页脚合计 + {@code records} 品规汇总行
     * @throws BusinessException {@code INV_DDC_204}、{@code INV_DDC_206}
     */
    public SummarizeDocumentDetailsResponse summarizeDocumentDetails(
        @Valid SummarizeDocumentDetailsRequest request
    ) {
        // Gate：业务域须合法
        DocumentDetailDomainScope scope = DocumentDetailDomainScope.requireFromCode(request.getDomainScope());
        // Pre/Asm：全量拉取后 SUM 进价与按品规分桶（无售价）
        return this.documentDetailReadLoader.summarizeDocumentDetails(scope, request);
    }

    /**
     * Excel 导出（IF-INV-DDC-04）；{@code presentationMode=SUMMARY} 时导出按品规汇总行。
     *
     * @param request 筛选与 {@code domainScope}、可选展示模式
     * @return 导出响应
     * @throws BusinessException {@code INV_DDC_204}、{@code INV_DDC_205}
     */
    public ExportDocumentDetailsResponse exportDocumentDetails(@Valid ExportDocumentDetailsRequest request) {
        // Gate：业务域须合法
        DocumentDetailDomainScope scope = DocumentDetailDomainScope.requireFromCode(request.getDomainScope());
        // Pre/Asm：全量拉取、可选品规聚合、行数上限与 Tabular Excel 写出
        return this.documentDetailReadLoader.exportDocumentDetails(scope, request);
    }
}
