/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog;

import com.openhis.chs.api.dto.catalog.CatalogFileDownloadOutput;
import com.openhis.chs.application.catalog.dto.CatalogDownloadVersionManifest;
import com.openhis.chs.application.catalog.support.CatalogDownloadJobRegistry;
import com.openhis.chs.application.catalog.support.CatalogDownloadZipCache;
import com.openhis.chs.application.catalog.support.CatalogFileParser;
import com.openhis.chs.application.catalog.support.CatalogFileParserRegistry;
import com.openhis.chs.domain.catalog.ChsCatalogConstants;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadPhaseSummaryDto;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadResultDto;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadVersionItemDto;
import com.openhis.chs.domain.catalog.entity.YbCatalogDownloadCursor;
import com.openhis.chs.domain.catalog.service.YbCatalogDownloadCursorDomainService;
import com.openhis.chs.domain.catalog.service.YbNationalCatalogDomainService;
import com.openhis.chs.domain.catalog.support.CatalogDownloadDisplaySupport;
import com.openhis.chs.domain.catalog.support.CatalogDownloadVersionSupport;
import com.openhis.chs.domain.support.ChsTextSupport;
import com.openhis.whale.core.infrastructure.multitenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 目录 ZIP 异步入库：按版本顺序从本地缓存解压 → 流式解析 → 批量 upsert + 游标快照。
 *
 * <p>适用于全部已注册 13xx 文件型目录（1301/1302/1305/1306/1307/1308/1309/1314/1315）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class CatalogDownloadImportService {

    private final CatalogDownloadJobRegistry jobRegistry;
    private final CatalogDownloadZipCache catalogDownloadZipCache;
    private final YbNationalCatalogDomainService catalogDomainService;
    private final YbCatalogDownloadCursorDomainService downloadCursorDomainService;
    private final TenantContext tenantContext;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "catalog-dl-import");
        t.setDaemon(true);
        return t;
    });

    /**
     * 异步入库任务参数。
     *
     * @param contractId  契约主键
     * @param tenantId    租户
     * @param operatorId  操作人
     * @param infno       13xx 接口号
     * @param startVer    起始版本
     * @param manifests   阶段一收集的版本清单
     */
    public record ImportJobRequest(
        Long contractId,
        Long tenantId,
        Long operatorId,
        String infno,
        String startVer,
        List<CatalogDownloadVersionManifest> manifests) {
    }

    /**
     * 提交异步入库任务并返回 jobId 供前端轮询。
     *
     * @param request 入库上下文与版本清单
     * @return 任务 id
     */
    public String submitImport(ImportJobRequest request) {
        String jobId = UUID.randomUUID().toString();
        int total;
        if (request.manifests() == null) {
            total = 0;
        } else {
            total = request.manifests().size();
        }
        jobRegistry.init(jobId, total);
        executor.submit(() -> runImport(jobId, request));
        log.info("[catalog-dl][import-submit] jobId={} infno={} versions={}", jobId, request.infno(), total);
        return jobId;
    }

    private void runImport(String jobId, ImportJobRequest request) {
        Long tenantId = request.tenantId();
        tenantContext.setCurrentTenantId(tenantId);
        try {
            jobRegistry.update(jobId, p -> {
                p.setPhase("import");
                p.setDetailMessage("开始从本地 ZIP 解压、解析并入库…");
            });

            List<CatalogDownloadVersionManifest> manifests = request.manifests();
            if (manifests == null || manifests.isEmpty()) {
                jobRegistry.complete(jobId, emptyResult(request.infno(), request.startVer()));
                return;
            }

            int totalUpsert = 0;
            int totalParsed = 0;
            int cursorSnapshots = 0;
            int processed = 0;
            YbCatalogDownloadCursor latestCursor = null;
            String lastFileQuryNo = null;
            String lastFilename = null;
            List<CatalogDownloadVersionItemDto> versionItems = new ArrayList<>(manifests.size());

            for (CatalogDownloadVersionManifest manifest : manifests) {
                processed++;
                String cacheVer = CatalogDownloadVersionSupport.normalizeForPlatformRequest(manifest.requestVer());
                byte[] zipBytes = catalogDownloadZipCache
                    .read(tenantId, request.contractId(), request.infno(), cacheVer)
                    .orElseThrow(() -> new IllegalStateException(
                        "ZIP 缓存缺失 ver=" + cacheVer + "，请重新拉取后再入库"));

                byte[] textBytes = CatalogFileParser.unzipToTextBytes(zipBytes);
                String fileQuryNo = manifest.meta().getFileQuryNo();
                String listType = CatalogFileParserRegistry.require(request.infno()).listType();
                boolean freshPackage = !catalogDomainService.existsFilePackage(
                    request.contractId(), tenantId, listType, fileQuryNo);

                int[] rowCountHolder = new int[1];
                int[] upsertedHolder = new int[1];
                String[] nextVersionHolder = new String[1];
                int batchSize = ChsCatalogConstants.CATALOG_UPSERT_BATCH_SIZE;

                String parsedNextVersion = CatalogFileParser.parseTextBytesStreaming(
                    request.infno(),
                    textBytes,
                    batchSize,
                    (batch, runningNextVersion) -> {
                        for (var row : batch) {
                            row.setSourceFileQuryNo(fileQuryNo);
                        }
                        rowCountHolder[0] += batch.size();
                        nextVersionHolder[0] = runningNextVersion;
                        upsertedHolder[0] += catalogDomainService.upsertFilePackageBatch(
                            request.contractId(),
                            tenantId,
                            request.operatorId(),
                            fileQuryNo,
                            batch,
                            freshPackage);
                    });

                int rowCount = rowCountHolder[0];
                int upserted = upsertedHolder[0];
                totalParsed += rowCount;
                totalUpsert += upserted;

                String resolvedNextVersion;
                if (parsedNextVersion != null) {
                    resolvedNextVersion = parsedNextVersion;
                } else {
                    resolvedNextVersion = nextVersionHolder[0];
                }
                String localAfter = StringUtils.defaultIfBlank(
                    resolvedNextVersion,
                    manifest.txtVersionName());
                lastFileQuryNo = manifest.meta().getFileQuryNo();
                lastFilename = manifest.meta().getFilename();
                latestCursor = downloadCursorDomainService.appendSnapshot(
                    request.contractId(),
                    request.infno(),
                    tenantId,
                    localAfter,
                    lastFileQuryNo,
                    lastFilename,
                    request.operatorId());
                cursorSnapshots++;

                versionItems.add(buildVersionItem(processed, manifest, rowCount));

                int finalProcessed = processed;
                int finalTotal = manifests.size();
                int finalTotalUpsert = totalUpsert;
                int finalTotalParsed = totalParsed;
                int finalUpserted = upserted;
                jobRegistry.update(jobId, p -> {
                    p.setProcessedVersions(finalProcessed);
                    p.setParsedRows(finalTotalParsed);
                    p.setUpsertedRows(finalTotalUpsert);
                    int percent;
                    if (finalTotal <= 0) {
                        percent = 100;
                    } else {
                        percent = (int) (finalProcessed * 100L / finalTotal);
                    }
                    p.setPercent(percent);
                    p.setDetailMessage(String.format(
                        "第 %d/%d 包：本包入库 %d 行，累计已入库 %d 行",
                        finalProcessed, finalTotal, finalUpserted, finalTotalUpsert));
                });

                log.info("[catalog-dl][import] jobId={} {}/{} ver={} upserted={} totalUpsert={}",
                    jobId, processed, manifests.size(), cacheVer, upserted, totalUpsert);
            }

            String finalLocalVer;
            if (latestCursor != null) {
                finalLocalVer = latestCursor.getLocalVer();
            } else {
                finalLocalVer = request.startVer();
            }
            Long latestCursorId;
            if (latestCursor != null) {
                latestCursorId = latestCursor.getId();
            } else {
                latestCursorId = null;
            }
            CatalogDownloadResultDto result = new CatalogDownloadResultDto()
                .setInfno(request.infno())
                .setUpsertCount(totalUpsert)
                .setVersionBatches(manifests.size())
                .setTotalVersionCount(manifests.size())
                .setVersions(versionItems)
                .setLocalVer(finalLocalVer)
                .setLatestCursorId(latestCursorId)
                .setCursorSnapshotCount(cursorSnapshots)
                .setPhaseSummaries(buildPhaseSummaries(
                    request.infno(), manifests.size(), totalParsed, totalUpsert, cursorSnapshots))
                .setHasMore(false)
                .setLastFileQuryNo(lastFileQuryNo)
                .setLastFilename(lastFilename);

            jobRegistry.complete(jobId, result);
            log.info("[catalog-dl][import-done] jobId={} versions={} totalUpsert={}",
                jobId, manifests.size(), totalUpsert);
        } catch (Exception ex) {
            log.error("[catalog-dl][import-failed] jobId={}", jobId, ex);
            jobRegistry.fail(jobId, ChsTextSupport.exceptionMessageOr(ex, ex.getClass().getSimpleName()));
        } finally {
            tenantContext.clear();
        }
    }

    private static CatalogDownloadResultDto emptyResult(String infno, String startVer) {
        return new CatalogDownloadResultDto()
            .setInfno(infno)
            .setUpsertCount(0)
            .setVersionBatches(0)
            .setTotalVersionCount(0)
            .setVersions(List.of())
            .setLocalVer(startVer)
            .setCursorSnapshotCount(0)
            .setHasMore(false);
    }

    private static CatalogDownloadVersionItemDto buildVersionItem(
            int index, CatalogDownloadVersionManifest manifest, int parsedRowCount) {
        String txtVer = manifest.txtVersionName();
        CatalogFileDownloadOutput meta = manifest.meta();
        return new CatalogDownloadVersionItemDto()
            .setIndex(index)
            .setRequestVer(manifest.requestVer())
            .setRequestVerShort(CatalogDownloadDisplaySupport.shortenVer(manifest.requestVer()))
            .setTxtVersionName(txtVer)
            .setTxtVersionNameShort(CatalogDownloadDisplaySupport.shortenVer(txtVer))
            .setNextVersion(txtVer)
            .setNextVersionShort(CatalogDownloadDisplaySupport.shortenVer(txtVer))
            .setNextDownloadVer(manifest.nextLocalVer())
            .setNextDownloadVerShort(CatalogDownloadDisplaySupport.shortenVer(manifest.nextLocalVer()))
            .setParsedRowCount(parsedRowCount)
            .setFileQuryNo(meta.getFileQuryNo())
            .setFilename(meta.getFilename())
            .setDataCnt(meta.getDataCnt())
            .setZipFromCache(true);
    }

    private static List<CatalogDownloadPhaseSummaryDto> buildPhaseSummaries(
            String infno, int versionCount, int parsedRows, int upsertedRows, int cursorSnapshots) {
        List<CatalogDownloadPhaseSummaryDto> list = new ArrayList<>(3);
        list.add(new CatalogDownloadPhaseSummaryDto()
            .setPhaseNo(1)
            .setPhaseKey("collect")
            .setTitle("阶段一：拉取 ZIP")
            .setDetail("版本链拉取完成，ZIP 已写入本地缓存")
            .setMetric(versionCount));
        list.add(new CatalogDownloadPhaseSummaryDto()
            .setPhaseNo(2)
            .setPhaseKey("parse")
            .setTitle("阶段二：解压并解析")
            .setDetail("按版本从 ZIP 解压 TXT 并解析为入库行")
            .setMetric(parsedRows));
        list.add(new CatalogDownloadPhaseSummaryDto()
            .setPhaseNo(3)
            .setPhaseKey("import")
            .setTitle("阶段三：逐版本入库")
            .setDetail("upsert 目录镜像并追加游标快照")
            .setMetric(upsertedRows));
        if (cursorSnapshots > 0) {
            list.get(2).setDetail(list.get(2).getDetail() + "，游标快照 " + cursorSnapshots + " 条");
        }
        return list;
    }
}
