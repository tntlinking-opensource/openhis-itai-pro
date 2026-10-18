/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.catalog.invoker;

import com.zxplat.plugin.chs.bridge.catalog.mapper.Catalog1317PlatformMapper;
import com.zxplat.plugin.chs.bridge.catalog.support.Catalog1317WriteBackSupport;
import com.zxplat.plugin.chs.bridge.catalog.support.CatalogPlatformSupport;
import com.zxplat.plugin.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.zxplat.plugin.chs.domain.catalog.command.Refresh1317Command;
import com.zxplat.plugin.chs.domain.catalog.command.UpsertInstitutionCatalogMatchCommand;
import com.zxplat.plugin.chs.domain.catalog.result.Refresh1317Result;
import com.zxplat.plugin.chs.domain.catalog.service.YbInstitutionCatalogMatchDomainService;
import com.zxplat.plugin.chs.domain.contract.ChsContractSnapshot;
import com.zxplat.plugin.chs.domain.share.constants.ChsCatalogConstants;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.domain.share.constants.ChsPlatformCodes;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.gateway.scheme.catalog.assembler.Catalog1317InputAssembler;
import com.zxplat.plugin.chs.gateway.scheme.catalog.model.Cat1317Input;
import com.zxplat.plugin.chs.gateway.scheme.catalog.parser.Cat1317OutputParser;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 【1317】医药机构目录匹配拉取出站：自动翻页 → upsert 本地镜像 → 正向回写院内对码。
 * <p>入参对齐老吉林插件全量下载：不传 {@code insu_admdvs}、默认不传 {@code list_type}
 * （传区划会把历史对照滤成很少几条）。</p>
 */
@Component
@RequiredArgsConstructor
public class Cat1317Invoker implements com.zxplat.plugin.chs.domain.catalog.access.Cat1317Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final Catalog1317InputAssembler catalog1317InputAssembler;
    private final YbInstitutionCatalogMatchDomainService matchDomainService;
    private final Catalog1317PlatformMapper catalog1317PlatformMapper;
    private final Catalog1317WriteBackSupport writeBackSupport;
    private final ChsInsuranceContractReader contractReader;
    private final CatalogPlatformSupport catalogPlatformSupport;

    /**
     * 调用 1317 拉取机构目录匹配并落库、正向回写。
     *
     * @param command 同步命令
     * @return 同步统计
     */
    @Override
    public Refresh1317Result invoke(Refresh1317Command command) {
        Refresh1317Command criteria = command;
        if (criteria == null) {
            criteria = new Refresh1317Command();
        }
        ChsContractSnapshot contract = contractReader.requireContract(
            criteria.getContractBusNo(), criteria.getContractId());
        String contractBusNo = contractReader.requireContractBusNo(contract);
        Long contractId = contract.id();
        Long operatorId = catalogPlatformSupport.requireOperatorId("请先登录后再同步机构目录匹配");
        String fixmedinsCode = catalogPlatformSupport.requireFixmedinsCode(contractBusNo);
        Long tenantId = TenantUtils.getCurrentTenantId();

        int pageSize;
        if (criteria.getPlatformPageSize() != null && criteria.getPlatformPageSize() > 0) {
            pageSize = Math.min(criteria.getPlatformPageSize(), 200);
        } else {
            pageSize = ChsCatalogConstants.DEFAULT_PLATFORM_PAGE_SIZE;
        }

        int upserted = 0;
        int writtenBack = 0;
        int skipped = 0;
        int pagesFetched = 0;
        Integer platformRecordCounts = null;
        Integer platformPages = null;

        // 与老插件一致：默认不按 list_type 拆分；仅当调用方显式传入时按类别过滤
        for (int pageNum = 1; pageNum <= ChsCatalogConstants.MAX_1317_REFRESH_PAGES; pageNum++) {
            // 关键：insu_admdvs 传 null，避免按契约区划过滤掉历史对照
            Cat1317Input input = catalog1317InputAssembler.assemble(
                fixmedinsCode, null, criteria, pageNum, pageSize);
            ResultBody body = gatewayClient.invoke(
                contractBusNo,
                ChsInfNo.CATALOG_ORG_MATCH_QUERY,
                input,
                catalogPlatformSupport.buildInvokeContext(operatorId));
            assertPlatformSuccess(body);
            pagesFetched++;

            Cat1317OutputParser.Page page = Cat1317OutputParser.parsePage(body);
            if (platformRecordCounts == null && page.recordCounts() != null) {
                platformRecordCounts = page.recordCounts();
            }
            if (platformPages == null && page.pages() != null) {
                platformPages = page.pages();
            }
            if (page.rows().isEmpty()) {
                break;
            }
            List<UpsertInstitutionCatalogMatchCommand> commands =
                catalog1317PlatformMapper.toUpsertCommands(page.rows());
            upserted += matchDomainService.upsertRows(contractId, tenantId, operatorId, commands);
            int[] wb = writeBackSupport.writeBack(commands, operatorId);
            writtenBack += wb[0];
            skipped += wb[1];

            // 老插件：有数据就继续翻页，直到空页；亦兼容本页不足 pageSize
            if (page.rows().size() < pageSize) {
                break;
            }
            if (platformPages != null && pageNum >= platformPages) {
                break;
            }
        }

        return new Refresh1317Result()
            .setUpserted(upserted)
            .setWrittenBack(writtenBack)
            .setSkipped(skipped)
            .setPagesFetched(pagesFetched)
            .setPlatformRecordCounts(platformRecordCounts)
            .setPlatformPages(platformPages);
    }

    /**
     * 平台失败时抛业务异常，避免半成功落库被误认为完成。
     *
     * @param body 平台响应
     */
    private static void assertPlatformSuccess(ResultBody body) {
        if (body == null || !ChsPlatformCodes.INFCODE_SUCCESS.equals(body.getInfcode())) {
            String msg = "empty response";
            if (body != null && body.getErrMsg() != null) {
                msg = body.getErrMsg();
            }
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "1317 平台失败: " + msg);
        }
    }
}
