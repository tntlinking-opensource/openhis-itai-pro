/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.catalog.workflow;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;

import com.zxplat.plugin.chs.bridge.catalog.support.dto.CatalogDownloadPhase1Context;
import com.zxplat.plugin.chs.bridge.catalog.support.dto.CatalogDownloadPhase1Result;
import com.zxplat.plugin.chs.bridge.catalog.support.dto.CatalogDownloadVersionBundle;
import com.zxplat.plugin.chs.domain.catalog.access.Cat9102FileDownloadAccess;
import com.zxplat.plugin.chs.bridge.catalog.invoker.Cat13xxInvoker;
import com.zxplat.plugin.chs.bridge.catalog.support.CatalogDownloadZipCache;
import com.zxplat.plugin.chs.bridge.catalog.support.CatalogFileParser;
import com.zxplat.plugin.chs.bridge.catalog.support.CatalogFileParserRegistry;
import com.zxplat.plugin.chs.domain.catalog.support.CatalogDownloadVersionSupport;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.plugin.chs.domain.share.support.ChsTextSupport;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.gateway.scheme.catalog.model.CatFileDownloadOutput;
import com.zxplat.plugin.chs.gateway.scheme.catalog.parser.CatFileDownloadOutputParser;
import com.zxplat.plugin.chs.domain.share.constants.ChsPlatformCodes;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 文件型目录下载阶段一工作流：版本链编排 + 缓存。
 * <p>出站由 {@link Cat13xxInvoker}、{@link Cat9102FileDownloadAccess} 完成；本类不调 gateway。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
public class CatalogDownloadPhase1Workflow {

    private final Cat13xxInvoker cat13xxInvoker;
    private final Cat9102FileDownloadAccess cat9102FileDownloadAccess;
    private final CatalogDownloadZipCache catalogDownloadZipCache;

    /** 按平台版本链拉取 13xx 元数据与 9102 ZIP（或读本地缓存）。 */
    public CatalogDownloadPhase1Result collectVersionBundles(CatalogDownloadPhase1Context context) {
        CatalogFileParserRegistry.require(context.infno());
        String contractBusNo = context.contractBusNo();
        Long contractId = context.contractId();
        Long tenantId = context.tenantId();
        String infno = context.infno();
        boolean clearZipCache = context.clearZipCache();

        if (clearZipCache) {
            try {
                int removed = catalogDownloadZipCache.clearVersions(tenantId, contractId, infno);
                log.info("[catalog-dl][phase1] clearZipCache=true cleared {} cached files", removed);
            } catch (IOException ex) {
                log.warn("[catalog-dl][phase1] 清空 ZIP 缓存失败: {}", ex.getMessage());
            }
        }

        List<CatalogDownloadVersionBundle> bundles = new ArrayList<>();
        String requestVer = CatalogDownloadVersionSupport.normalizeForPlatformRequest(context.startVer());
        int round = 0;
        boolean stoppedByPreviewRoundCap = false;

        while (true) {
            round++;
            String platformVer = CatalogDownloadVersionSupport.normalizeForPlatformRequest(requestVer);

            if (!clearZipCache && tryCollectFullyCachedBundle(
                bundles, infno, tenantId, contractId, requestVer, platformVer)) {
                CatalogDownloadVersionBundle last = bundles.get(bundles.size() - 1);
                if (shouldStopAfterPackage(infno, platformVer, last.nextLocalVer())) {
                    break;
                }
                requestVer = last.nextLocalVer().trim();
                if (applyPreviewRoundCap(context, round, bundles)) {
                    stoppedByPreviewRoundCap = true;
                    break;
                }
                continue;
            }

            log.info("[catalog-dl][phase1] 轮次={} {} data.ver={}", round, infno, platformVer);

            ResultBody body = cat13xxInvoker.invoke(
                contractBusNo, infno, requestVer, context.invokeContext());

            if (!isPlatformSuccess(body)) {
                if (bundles.isEmpty()) {
                    assertPlatformDownloadSuccess(body, infno);
                }
                String infcodeLog = body == null ? null : body.getInfcode();
                log.info("[catalog-dl][phase1] break: 平台 {} infcode={} errMsg={}",
                    infno, infcodeLog, ChsTextSupport.platformErrMsg(body, null));
                break;
            }

            CatFileDownloadOutput downloadMeta = CatFileDownloadOutputParser.parse(body);
            if (downloadMeta == null
                || isEmpty(downloadMeta.getFileQuryNo())
                || isEmpty(downloadMeta.getFilename())) {
                log.info("[catalog-dl][phase1] break: 平台无文件元数据 infno={}", infno);
                break;
            }

            if (downloadMeta.getDataCnt() != null && downloadMeta.getDataCnt() <= 0) {
                log.warn("[catalog-dl][phase1] data_cnt={} 仍尝试 9102（与历史 一致）",
                    downloadMeta.getDataCnt());
            }

            persistMeta(tenantId, contractId, infno, platformVer, downloadMeta);

            ZipResolve zipResolve = resolveZipBytes(
                contractBusNo, contractId, tenantId, infno, platformVer,
                context.fixmedinsCode(), downloadMeta, context.invokeContext(), clearZipCache);
            appendBundle(bundles, requestVer, downloadMeta, zipResolve, infno);

            CatalogDownloadVersionBundle last = bundles.get(bundles.size() - 1);
            if (shouldStopAfterPackage(infno, platformVer, last.nextLocalVer())) {
                break;
            }

            requestVer = last.nextLocalVer().trim();

            if (applyPreviewRoundCap(context, round, bundles)) {
                stoppedByPreviewRoundCap = true;
                break;
            }
        }

        log.info("[catalog-dl][phase1] 版本链结束，共 {} 个 ZIP（平台驱动停链）", bundles.size());
        return new CatalogDownloadPhase1Result(bundles, stoppedByPreviewRoundCap);
    }

    private boolean tryCollectFullyCachedBundle(
            List<CatalogDownloadVersionBundle> bundles,
            String infno,
            Long tenantId,
            Long contractId,
            String requestVer,
            String platformVer) {
        if (!catalogDownloadZipCache.exists(tenantId, contractId, infno, platformVer)) {
            return false;
        }
        Optional<CatFileDownloadOutput> cachedMeta =
            catalogDownloadZipCache.readMeta(tenantId, contractId, infno, platformVer);
        if (cachedMeta.isEmpty()) {
            return false;
        }
        Optional<byte[]> zipBytes = catalogDownloadZipCache.read(tenantId, contractId, infno, platformVer);
        if (zipBytes.isEmpty()) {
            return false;
        }

        ZipResolve zipResolve = new ZipResolve(zipBytes.get(), true);
        appendBundle(bundles, requestVer, cachedMeta.get(), zipResolve, infno);
        log.info("[catalog-dl][phase1] 缓存命中，跳过 13xx/9102 ver={} fileQuryNo={}",
            platformVer, cachedMeta.get().getFileQuryNo());
        return true;
    }

    private void appendBundle(
            List<CatalogDownloadVersionBundle> bundles,
            String requestVer,
            CatFileDownloadOutput downloadMeta,
            ZipResolve zipResolve,
            String infno) {
        byte[] zipBytes = zipResolve.bytes();
        CatalogFileParser.TextPackageMetadata txtMeta =
            CatalogFileParser.scanZipPackageMetadata(infno, zipBytes);

        log.info("[catalog-dl][phase1] 完成 ver={} fileQuryNo={} rowsHint={} txtLines={} zip={}B zipFromCache={} verNoLast={} verNoMax={} verNameLast={} nextLocalVer={}",
            requestVer, downloadMeta.getFileQuryNo(), downloadMeta.getDataCnt(), txtMeta.dataLineCount(),
            zipBytes.length, zipResolve.fromCache(), txtMeta.txtLastVerNo(), txtMeta.txtMaxVerNo(),
            txtMeta.txtVersionName(), txtMeta.nextLocalVer());

        bundles.add(new CatalogDownloadVersionBundle(
            requestVer, downloadMeta, zipResolve.fromCache(),
            txtMeta.dataLineCount(), txtMeta.txtVersionName(), txtMeta.nextLocalVer()));
    }

    private static boolean applyPreviewRoundCap(
            CatalogDownloadPhase1Context context, int round, List<CatalogDownloadVersionBundle> bundles) {
        Integer maxRounds = context.maxRounds();
        if (maxRounds != null && maxRounds > 0 && round >= maxRounds) {
            log.info("[catalog-dl][phase1] break: 探测轮次上限 maxRounds={} bundles={}", maxRounds, bundles.size());
            return true;
        }
        return false;
    }

    private void persistMeta(
            Long tenantId,
            Long contractId,
            String infno,
            String catalogVer,
            CatFileDownloadOutput downloadMeta) {
        try {
            catalogDownloadZipCache.writeMeta(tenantId, contractId, infno, catalogVer, downloadMeta);
        } catch (IOException ex) {
            log.warn("[catalog-dl][phase1] 元数据写入缓存失败 catalogVer={}: {}", catalogVer, ex.getMessage());
        }
    }

    private record ZipResolve(byte[] bytes, boolean fromCache) {
    }

    private ZipResolve resolveZipBytes(
            String contractBusNo,
            Long contractId,
            Long tenantId,
            String infno,
            String catalogVer,
            String fixmedinsCode,
            CatFileDownloadOutput downloadMeta,
            com.zxplat.plugin.chs.gateway.client.InvokeContext invokeContext,
            boolean clearZipCache) {
        if (!clearZipCache && catalogDownloadZipCache.exists(tenantId, contractId, infno, catalogVer)) {
            return catalogDownloadZipCache
                .read(tenantId, contractId, infno, catalogVer)
                .map(bytes -> {
                    log.info("[catalog-dl][phase1] ZIP 已缓存，跳过 9102 ver={}", catalogVer);
                    return new ZipResolve(bytes, true);
                })
                .orElseGet(() -> new ZipResolve(downloadAndCacheZip(
                    contractBusNo, contractId, tenantId, infno, catalogVer, fixmedinsCode,
                    downloadMeta, invokeContext), false));
        }
        return new ZipResolve(downloadAndCacheZip(
            contractBusNo, contractId, tenantId, infno, catalogVer, fixmedinsCode,
            downloadMeta, invokeContext), false);
    }

    private byte[] downloadAndCacheZip(
            String contractBusNo,
            Long contractId,
            Long tenantId,
            String infno,
            String catalogVer,
            String fixmedinsCode,
            CatFileDownloadOutput downloadMeta,
            com.zxplat.plugin.chs.gateway.client.InvokeContext invokeContext) {
        log.info("[catalog-dl][phase1] 9102 下载 ZIP ver={} fileQuryNo={}", catalogVer, downloadMeta.getFileQuryNo());
        byte[] zipBytes = cat9102FileDownloadAccess.download(
            contractBusNo, downloadMeta.getFileQuryNo(), downloadMeta.getFilename(), fixmedinsCode);
        try {
            catalogDownloadZipCache.write(tenantId, contractId, infno, catalogVer, zipBytes);
            catalogDownloadZipCache.writeMeta(tenantId, contractId, infno, catalogVer, downloadMeta);
        } catch (IOException ex) {
            log.warn("目录 ZIP 写入缓存失败 catalogVer={}: {}", catalogVer, ex.getMessage());
        }
        return zipBytes;
    }

    private static boolean isPlatformSuccess(ResultBody body) {
        return body != null && ChsPlatformCodes.INFCODE_SUCCESS.equals(body.getInfcode());
    }

    private static void assertPlatformDownloadSuccess(ResultBody body, String infno) {
        if (!isPlatformSuccess(body)) {
            String msg = ChsTextSupport.platformErrMsg(body, "empty response");
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, infno + " 目录下载失败: " + msg);
        }
    }

    /** 本包解析后是否应停链。 */
    static boolean shouldStopAfterPackage(String infno, String requestVer, String nextLocalVer) {
        if (isEmpty(nextLocalVer)) {
            log.info("[catalog-dl][phase1] break: {} 本包 TXT 无下一版本名 requestVer={}", infno, requestVer);
            return true;
        }
        String nextPlatform = CatalogDownloadVersionSupport.normalizeForPlatformRequest(nextLocalVer);
        String currentPlatform = CatalogDownloadVersionSupport.normalizeForPlatformRequest(requestVer);
        if (nextPlatform.equals(currentPlatform)) {
            log.info("[catalog-dl][phase1] break: {} 版本无推进 requestVer={} nextLocalVer={}",
                infno, requestVer, nextLocalVer.trim());
            return true;
        }
        return false;
    }
}
