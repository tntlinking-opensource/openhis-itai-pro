/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.catalog.workflow;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.bridge.catalog.support.CatalogDownloadDisplaySupport;
import com.openhis.chs.bridge.catalog.support.CatalogDownloadImportService;
import com.openhis.chs.bridge.catalog.support.CatalogDownloadJobRegistry;
import com.openhis.chs.bridge.catalog.support.CatalogFileParserRegistry;
import com.openhis.chs.bridge.catalog.support.CatalogPlatformSupport;
import com.openhis.chs.bridge.catalog.support.dto.CatalogDownloadPhase1Context;
import com.openhis.chs.bridge.catalog.support.dto.CatalogDownloadPhase1Result;
import com.openhis.chs.bridge.catalog.support.dto.CatalogDownloadVersionBundle;
import com.openhis.chs.bridge.catalog.support.dto.CatalogDownloadVersionManifest;
import com.openhis.chs.domain.catalog.access.CatalogDownloadAccess;
import com.openhis.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.openhis.chs.domain.catalog.command.StartCatalogDownloadCommand;
import com.openhis.chs.domain.catalog.entity.YbCatalogDownloadCursor;
import com.openhis.chs.domain.catalog.result.CatalogDownloadJobProgressResult;
import com.openhis.chs.domain.catalog.result.CatalogDownloadJobStartResult;
import com.openhis.chs.domain.catalog.result.CatalogDownloadPreviewResult;
import com.openhis.chs.domain.catalog.result.CatalogDownloadVersionItemResult;
import com.openhis.chs.domain.catalog.service.YbCatalogDownloadCursorDomainService;
import com.openhis.chs.domain.catalog.support.CatalogDownloadStartVerSupport;
import com.openhis.chs.domain.contract.ChsContractSnapshot;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.catalog.model.CatFileDownloadOutput;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件型目录下载 bridge 编排实现（P13）。
 *
 * <p><b>职责：</b>Phase1 经 13xx/9102 拉 ZIP，提交异步入库任务并暴露进度查询。</p>
 * <p><b>实现端口：</b>{@link com.openhis.chs.domain.catalog.access.CatalogDownloadAccess}</p>
 * <p><b>调用方：</b>{@code InsuranceCatalogAppService}</p>
 * <p><b>架构边界：</b>bridge 层组装 gateway 出站上下文；application 只依赖 domain 端口。</p>
 * <p><b>迁移阶段：</b>P13</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class CatalogDownloadWorkflow implements CatalogDownloadAccess {

    private final CatalogDownloadPhase1Workflow catalogDownloadPhase1Workflow;
    private final CatalogDownloadImportService catalogDownloadImportService;
    private final CatalogDownloadJobRegistry catalogDownloadJobRegistry;
    private final ChsInsuranceContractReader contractReader;
    private final YbCatalogDownloadCursorDomainService downloadCursorDomainService;
    private final CatalogPlatformSupport catalogPlatformSupport;

    @Override
    public CatalogDownloadPreviewResult previewCatalogDownload(StartCatalogDownloadCommand request) {
        DownloadContext ctx = prepareDownloadContext(request, true);
        Integer previewMaxRounds = resolvePreviewMaxRounds(request);
        log.info("[catalog-dl][preview] contractBusNo={} infno={} startVer={} previewMaxRounds={}",
            ctx.contractBusNo(), ctx.infno(), ctx.startVer(), previewMaxRounds);
        CatalogDownloadPhase1Result phase1 = runPhase1(ctx, previewMaxRounds);
        CatalogDownloadPreviewResult result = buildPreviewDto(ctx.infno(), ctx.startVer(), phase1);
        log.info("[catalog-dl][preview] 结束 pendingCount={} hasMore={}",
            result.getPendingVersionCount(), result.isHasMore());
        return result;
    }

    @Override
    public CatalogDownloadJobStartResult startCatalogDownload(StartCatalogDownloadCommand request) {
        DownloadContext ctx = prepareDownloadContext(request, true);
        log.info("[catalog-dl][start] contractBusNo={} contractId={} infno={} startVer={}",
            ctx.contractBusNo(), ctx.contractId(), ctx.infno(), ctx.startVer());

        CatalogDownloadPhase1Result phase1 = runPhase1(ctx, null);
        List<CatalogDownloadVersionManifest> manifests = new ArrayList<>(phase1.bundles().size());
        for (CatalogDownloadVersionBundle bundle : phase1.bundles()) {
            manifests.add(CatalogDownloadVersionManifest.fromBundle(bundle));
        }
        log.info("[catalog-dl][phase1-done] versionCount={} 提交异步入库", manifests.size());

        if (manifests.isEmpty()) {
            return new CatalogDownloadJobStartResult()
                .setInfno(ctx.infno())
                .setTotalVersions(0)
                .setMessage("无新版本 ZIP，未提交入库任务");
        }

        String jobId = catalogDownloadImportService.submitImport(
            new CatalogDownloadImportService.ImportJobRequest(
                ctx.contractId(),
                ctx.tenantId(),
                ctx.operatorId(),
                ctx.infno(),
                ctx.startVer(),
                manifests));

        return new CatalogDownloadJobStartResult()
            .setJobId(jobId)
            .setInfno(ctx.infno())
            .setTotalVersions(manifests.size())
            .setMessage("ZIP 拉取完成，正在后台解压、解析并入库，请轮询进度");
    }

    @Override
    public CatalogDownloadJobProgressResult getCatalogDownloadJobProgress(String jobId) {
        if (isEmpty(jobId)) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "jobId 不能为空");
        }
        CatalogDownloadJobProgressResult progress = catalogDownloadJobRegistry.get(jobId.trim());
        if (progress == null) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "入库任务不存在或已过期: " + jobId);
        }
        return progress;
    }

    private DownloadContext prepareDownloadContext(StartCatalogDownloadCommand request, boolean requireLogin) {
        ChsContractSnapshot contract = contractReader.requireContract(
            request.getContractBusNo(), request.getContractId());
        String contractBusNo = contractReader.requireContractBusNo(contract);
        Long contractId = contract.id();
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long operatorId;
        if (requireLogin) {
            operatorId = catalogPlatformSupport.requireOperatorId("请先登录后再下载医保目录");
        } else {
            operatorId = catalogPlatformSupport.findOperatorIdOrNull();
        }
        if (operatorId == null) {
            operatorId = 0L;
        }
        String infno = StringUtils.defaultIfBlank(request.getInfno(), ChsInfNo.DRUG_CATALOG_DOWNLOAD).trim();
        CatalogFileParserRegistry.require(infno);
        String verBranch = trimToNull(request.getVerBranch());
        String cursorVer = null;
        if (!Boolean.TRUE.equals(request.getForceStartFromZero())) {
            // 1305：按 verBranch（F002/F003）取本分支最新游标，避免 F003 误用 F002 链尾
            cursorVer = downloadCursorDomainService.findLatest(contractId, infno, tenantId, verBranch)
                .map(YbCatalogDownloadCursor::getLocalVer)
                .filter(StringUtils::isNotEmpty)
                .orElse(null);
        }
        String startVer = CatalogDownloadStartVerSupport.resolveStartVer(
            infno,
            verBranch,
            Boolean.TRUE.equals(request.getForceStartFromZero()),
            request.getStartLocalVer(),
            cursorVer);
        if (Boolean.TRUE.equals(request.getForceStartFromZero())) {
            log.info("[catalog-dl][ctx] forceStartFromZero=true，忽略游标，从 ver={} 开始 infno={} verBranch={}",
                startVer, infno, verBranch);
        } else if ("1305".equals(infno)) {
            log.info("[catalog-dl][ctx] 1305 verBranch={} cursorVer={} startVer={}",
                verBranch, cursorVer, startVer);
        }
        boolean clearZipCache = Boolean.TRUE.equals(request.getClearZipCache());
        if (clearZipCache) {
            log.info("[catalog-dl][ctx] clearZipCache=true，将清空 {} 本地 ZIP 缓存后重拉 9102", infno);
        }
        InvokeContext invokeContext = catalogPlatformSupport.buildInvokeContext(operatorId);
        String fixmedinsCode = catalogPlatformSupport.requireFixmedinsCode(contractBusNo);
        log.debug("[catalog-dl][ctx] tenantId={} infno={} startVer={} fixmedinsCode={} clearZipCache={}",
            tenantId, infno, startVer, fixmedinsCode, clearZipCache);
        return new DownloadContext(
            contractBusNo, contractId, tenantId, operatorId, infno,
            startVer, invokeContext, fixmedinsCode, clearZipCache);
    }

    private static Integer resolvePreviewMaxRounds(StartCatalogDownloadCommand request) {
        if (request.getMaxVersions() != null && request.getMaxVersions() > 0) {
            return request.getMaxVersions();
        }
        return null;
    }

    private CatalogDownloadPhase1Result runPhase1(DownloadContext ctx, Integer maxRounds) {
        CatalogDownloadPhase1Context phase1Context = new CatalogDownloadPhase1Context(
            ctx.contractBusNo(),
            ctx.contractId(),
            ctx.tenantId(),
            ctx.infno(),
            ctx.startVer(),
            ctx.fixmedinsCode(),
            ctx.invokeContext(),
            ctx.clearZipCache(),
            maxRounds);
        return catalogDownloadPhase1Workflow.collectVersionBundles(phase1Context);
    }

    private CatalogDownloadPreviewResult buildPreviewDto(
            String infno, String startVer, CatalogDownloadPhase1Result phase1) {
        List<CatalogDownloadVersionItemResult> versions = toVersionItemResults(phase1.bundles());
        return new CatalogDownloadPreviewResult()
            .setInfno(infno)
            .setStartLocalVer(startVer)
            .setStartLocalVerShort(CatalogDownloadDisplaySupport.shortenVer(startVer))
            .setPendingVersionCount(versions.size())
            .setHasMore(phase1.stoppedByPreviewRoundCap())
            .setVersions(versions);
    }

    private static List<CatalogDownloadVersionItemResult> toVersionItemResults(
            List<CatalogDownloadVersionBundle> bundles) {
        List<CatalogDownloadVersionItemResult> versions = new ArrayList<>(bundles.size());
        int index = 0;
        for (CatalogDownloadVersionBundle bundle : bundles) {
            index++;
            versions.add(buildVersionItemResult(index, bundle));
        }
        return versions;
    }

    private static CatalogDownloadVersionItemResult buildVersionItemResult(
            int index, CatalogDownloadVersionBundle bundle) {
        String txtVer = bundle.txtVersionName();
        CatFileDownloadOutput meta = bundle.meta();
        return new CatalogDownloadVersionItemResult()
            .setIndex(index)
            .setRequestVer(bundle.requestVer())
            .setRequestVerShort(CatalogDownloadDisplaySupport.shortenVer(bundle.requestVer()))
            .setTxtVersionName(txtVer)
            .setTxtVersionNameShort(CatalogDownloadDisplaySupport.shortenVer(txtVer))
            .setNextVersion(txtVer)
            .setNextVersionShort(CatalogDownloadDisplaySupport.shortenVer(txtVer))
            .setNextDownloadVer(bundle.nextLocalVer())
            .setNextDownloadVerShort(CatalogDownloadDisplaySupport.shortenVer(bundle.nextLocalVer()))
            .setParsedRowCount(bundle.txtLineCount())
            .setFileQuryNo(meta.getFileQuryNo())
            .setFilename(meta.getFilename())
            .setDataCnt(meta.getDataCnt())
            .setZipFromCache(bundle.zipFromCache());
    }

    private record DownloadContext(
        String contractBusNo,
        Long contractId,
        Long tenantId,
        Long operatorId,
        String infno,
        String startVer,
        InvokeContext invokeContext,
        String fixmedinsCode,
        boolean clearZipCache) {
    }
}
