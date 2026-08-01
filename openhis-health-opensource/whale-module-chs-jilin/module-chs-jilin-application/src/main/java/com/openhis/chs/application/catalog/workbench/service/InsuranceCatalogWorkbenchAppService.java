/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.catalog.workbench.service;

import com.openhis.chs.domain.catalog.access.Cat1317Invoker;
import com.openhis.chs.domain.catalog.access.CatalogMappingUploadAccess;
import com.openhis.chs.domain.catalog.command.PageInstitutionCatalogMatchCommand;
import com.openhis.chs.domain.catalog.command.PagePendingCatalogMappingCommand;
import com.openhis.chs.domain.catalog.command.Refresh1317Command;
import com.openhis.chs.domain.catalog.command.RevokeFromInstitutionMatchCommand;
import com.openhis.chs.domain.catalog.repository.PendingCatalogMappingLookup;
import com.openhis.chs.domain.catalog.result.Catalog3302RevokeResult;
import com.openhis.chs.domain.catalog.result.PageInstitutionCatalogMatchResult;
import com.openhis.chs.domain.catalog.result.PagePendingCatalogMappingResult;
import com.openhis.chs.domain.catalog.result.Refresh1317Result;
import com.openhis.chs.domain.catalog.service.YbInstitutionCatalogMatchQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 医保目录对码 Workbench 页（POST /api/app/insuranceCatalogWorkbench/*）。
 * <p>对应 AS-CHS-CAT-004：院内待对码列表 + 平台已对照（1317）镜像查询/同步。</p>
 */
@Service
@RequiredArgsConstructor
public class InsuranceCatalogWorkbenchAppService {

    private final PendingCatalogMappingLookup pendingCatalogMappingLookup;
    private final YbInstitutionCatalogMatchQueryService institutionCatalogMatchQueryService;
    private final Cat1317Invoker cat1317Invoker;
    private final CatalogMappingUploadAccess catalogMappingUploadAccess;

    /**
     * IF-CHS-CAT-004-R-01：分页查询院内待处理（相对平台 1317 镜像无对照）。
     *
     * @param command 分页筛选（须含 contractBusNo）
     * @return 待对码分页结果
     */
    public PagePendingCatalogMappingResult pagePendingMappings(PagePendingCatalogMappingCommand command) {
        if (command == null || command.getContractBusNo() == null || command.getContractBusNo().isBlank()) {
            throw new BusinessException("CONTRACT_BUS_NO_REQUIRED", "请选择医保契约后再查询院内待处理");
        }
        return pendingCatalogMappingLookup.page(command);
    }

    /**
     * IF-CHS-CAT-004-R-02：分页查询本地 1317 机构目录匹配镜像。
     *
     * @param command 分页筛选（须含 contractBusNo）
     * @return 本地镜像分页
     */
    public PageInstitutionCatalogMatchResult queryInstitutionCatalogMatchPage(
            PageInstitutionCatalogMatchCommand command) {
        // Gate：契约 bus_no 必填，否则无法定位镜像分片
        if (command == null || command.getContractBusNo() == null || command.getContractBusNo().isBlank()) {
            throw new BusinessException("CONTRACT_BUS_NO_REQUIRED", "请选择医保契约后再查询平台已对照");
        }
        // Pre/Asm：QueryService 解析契约并分页返回（含院内启用/停用/无匹配标注）
        return institutionCatalogMatchQueryService.page(command);
    }

    /**
     * IF-CHS-CAT-004-W-02：从平台同步 1317（自动翻页落库 + 正向回写院内对码）。
     *
     * @param command 同步筛选（须含 contractBusNo）
     * @return 同步统计
     */
    public Refresh1317Result refreshFromPlatform1317(Refresh1317Command command) {
        // Gate：契约必填；未签到/未登录由 Invoker 内抛错
        if (command == null || command.getContractBusNo() == null || command.getContractBusNo().isBlank()) {
            throw new BusinessException("CONTRACT_BUS_NO_REQUIRED", "请选择医保契约后再同步 1317");
        }
        // Pre：无本地预取；Cmd：出站 1317 → upsert → 正向回写；Asm：直接返回统计 Result
        return cat1317Invoker.invoke(command);
    }

    /**
     * IF-CHS-CAT-004-W-03：按平台已对照镜像行撤销 3302（含院内停用/孤儿）。
     *
     * @param command 镜像撤销命令
     * @return 撤销结果
     */
    public Catalog3302RevokeResult revokePlatformCatalogMatch3302(RevokeFromInstitutionMatchCommand command) {
        if (command == null || command.getContractBusNo() == null || command.getContractBusNo().isBlank()) {
            throw new BusinessException("CONTRACT_BUS_NO_REQUIRED", "请选择医保契约后再撤销对照");
        }
        if (command.getMatchId() == null) {
            throw new BusinessException("MATCH_ID_REQUIRED", "请指定要撤销的平台已对照行");
        }
        return catalogMappingUploadAccess.revokeFromInstitutionMatch(command);
    }
}
