/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.ChsPlatformCodes;
import com.openhis.chs.api.InsuranceGatewayClient;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.api.dto.catalog.CatalogFileDownloadOutput;
import com.openhis.chs.api.support.ChsOutputParser;
import com.openhis.chs.application.catalog.assembler.Catalog9102InputAssembler;
import com.openhis.chs.application.catalog.assembler.CatalogFileDownloadInputAssembler;
import com.openhis.chs.application.catalog.dto.CatalogDownloadPhase1Context;
import com.openhis.chs.application.catalog.dto.CatalogDownloadPhase1Result;
import com.openhis.chs.application.catalog.dto.CatalogDownloadVersionBundle;
import com.openhis.chs.application.catalog.support.CatalogDownloadZipCache;
import com.openhis.chs.application.catalog.support.CatalogFileParser;
import com.openhis.chs.application.catalog.support.CatalogFileParserRegistry;
import com.openhis.chs.domain.ChsErrorCodes;
import com.openhis.chs.domain.catalog.support.CatalogDownloadVersionSupport;
import com.openhis.chs.domain.support.ChsTextSupport;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 文件型目录下载阶段一：13xx 版本链 + 9102/缓存（对齐 OpenHIS {@code StartUpdate}）。
 *
 * <p>已缓存 ZIP（及元数据）时跳过 9102；元数据齐全时连 13xx 也可跳过。</p>
 * <p>停链仅依赖平台：{@code infcode!=0}、无 {@code file_qury_no}/filename；无轮次硬上限。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class CatalogDownloadPhase1Service {

    private final InsuranceGatewayClient gatewayClient;
    private final CatalogFileDownloadInputAssembler catalogFileDownloadInputAssembler;
    private final Catalog9102InputAssembler catalog9102InputAssembler;
    private final CatalogDownloadZipCache catalogDownloadZipCache;

    /**
     * 阶段一：按平台版本链拉取 13xx 元数据与 9102 ZIP（或读本地缓存）。
     *
     * @param context 契约、起始版本、是否清缓存、探测轮次上限等
     * @return 版本 bundle 列表及是否因探测轮次上限停止
     */
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
                log.info("[catalog-dl][phase1] clearZipCache=true 已清空 {} 个缓存文件", removed);
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

            JSONObject input = catalogFileDownloadInputAssembler.assemble(requestVer);
            ResultBody body = gatewayClient.invoke(
                contractBusNo, infno, input, context.invokeContext());

            if (!isPlatformSuccess(body)) {
                if (bundles.isEmpty()) {
                    assertPlatformDownloadSuccess(body, infno);
                }
                String infcodeLog;
                if (body == null) {
                    infcodeLog = null;
                } else {
                    infcodeLog = body.getInfcode();
                }
                log.info("[catalog-dl][phase1] break: 平台 {} infcode={} errMsg={}",
                    infno,
                    infcodeLog,
                    ChsTextSupport.platformErrMsg(body, null));
                break;
            }

            CatalogFileDownloadOutput downloadMeta = ChsOutputParser.parseCatalogFileDownloadOutput(body);
            if (downloadMeta == null
                || StringUtils.isBlank(downloadMeta.getFileQuryNo())
                || StringUtils.isBlank(downloadMeta.getFilename())) {
                log.info("[catalog-dl][phase1] break: 平台无文件元数据 infno={}", infno);
                break;
            }

            if (downloadMeta.getDataCnt() != null && downloadMeta.getDataCnt() <= 0) {
                log.warn("[catalog-dl][phase1] data_cnt={} 仍尝试 9102（与 OpenHIS 一致）",
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

        log.info("[catalog-dl][phase1] 版本链结束 共 {} 个 ZIP（平台驱动停链）", bundles.size());
        return new CatalogDownloadPhase1Result(bundles, stoppedByPreviewRoundCap);
    }

    /**
     * ZIP 与元数据均在本地时，跳过 13xx/9102，仅读缓存推进版本链。
     *
     * @return true 表示本包已从缓存收集并应继续下一轮
     */
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
        Optional<CatalogFileDownloadOutput> cachedMeta =
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
            CatalogFileDownloadOutput downloadMeta,
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
            CatalogFileDownloadOutput downloadMeta) {
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
            CatalogFileDownloadOutput downloadMeta,
            com.openhis.chs.api.InvokeContext invokeContext,
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
            CatalogFileDownloadOutput downloadMeta,
            com.openhis.chs.api.InvokeContext invokeContext) {
        JSONObject input9102 = catalog9102InputAssembler.assemble(
            downloadMeta.getFileQuryNo(),
            downloadMeta.getFilename(),
            fixmedinsCode);
        log.info("[catalog-dl][phase1] 9102 下载 ZIP ver={} fileQuryNo={}", catalogVer, downloadMeta.getFileQuryNo());
        byte[] zipBytes = gatewayClient.downloadFile(contractBusNo, input9102, invokeContext);
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

    /**
     * 本包解析后是否应停链（避免平台持续 {@code infcode=0} 但版本无推进时的死循环，1307 等常见）。
     */
    static boolean shouldStopAfterPackage(String infno, String requestVer, String nextLocalVer) {
        if (StringUtils.isBlank(nextLocalVer)) {
            log.info("[catalog-dl][phase1] break: {} 本包 TXT 无下一版本号 requestVer={}", infno, requestVer);
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
