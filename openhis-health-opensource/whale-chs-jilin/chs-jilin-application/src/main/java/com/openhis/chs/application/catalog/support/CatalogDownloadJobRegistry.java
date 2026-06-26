/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.support;

import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadJobProgressDto;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadResultDto;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
public class CatalogDownloadJobRegistry {

    private final Map<String, CatalogDownloadJobProgressDto> jobs = new ConcurrentHashMap<>();

    public void init(String jobId, int totalVersions) {
        jobs.put(jobId, new CatalogDownloadJobProgressDto()
            .setJobId(jobId)
            .setPhase("waiting")
            .setTotalVersions(totalVersions)
            .setProcessedVersions(0)
            .setParsedRows(0)
            .setUpsertedRows(0)
            .setPercent(0)
            .setDetailMessage("等待开始解压与入库…")
            .setFinished(false)
            .setSuccess(false));
    }

    public void update(String jobId, Consumer<CatalogDownloadJobProgressDto> updater) {
        CatalogDownloadJobProgressDto current = jobs.get(jobId);
        if (current == null) {
            return;
        }
        updater.accept(current);
    }

    public void complete(String jobId, CatalogDownloadResultDto result) {
        update(jobId, p -> {
            p.setPhase("done");
            p.setFinished(true);
            p.setSuccess(true);
            p.setPercent(100);
            p.setResult(result);
            p.setDetailMessage("全部 ZIP 已解压、解析并入库完成");
        });
    }

    public void fail(String jobId, String message) {
        update(jobId, p -> {
            p.setPhase("failed");
            p.setFinished(true);
            p.setSuccess(false);
            p.setErrorMessage(message);
            p.setDetailMessage(message);
        });
    }

    public CatalogDownloadJobProgressDto get(String jobId) {
        CatalogDownloadJobProgressDto dto = jobs.get(jobId);
        if (dto == null) {
            return null;
        }
        return copyOf(dto);
    }

    private static CatalogDownloadJobProgressDto copyOf(CatalogDownloadJobProgressDto src) {
        return new CatalogDownloadJobProgressDto()
            .setJobId(src.getJobId())
            .setPhase(src.getPhase())
            .setTotalVersions(src.getTotalVersions())
            .setProcessedVersions(src.getProcessedVersions())
            .setParsedRows(src.getParsedRows())
            .setUpsertedRows(src.getUpsertedRows())
            .setPercent(src.getPercent())
            .setDetailMessage(src.getDetailMessage())
            .setFinished(src.isFinished())
            .setSuccess(src.isSuccess())
            .setResult(src.getResult())
            .setErrorMessage(src.getErrorMessage());
    }
}
