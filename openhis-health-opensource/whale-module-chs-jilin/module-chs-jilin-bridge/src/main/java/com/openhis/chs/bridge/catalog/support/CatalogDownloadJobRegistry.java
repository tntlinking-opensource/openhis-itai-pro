/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.catalog.support;

import com.openhis.chs.domain.catalog.result.CatalogDownloadJobProgressResult;
import com.openhis.chs.domain.catalog.result.CatalogDownloadResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 目录异步入库任务进度注册表（进程内内存，供前端轮询）。
 * <p>由 {@link com.openhis.chs.bridge.catalog.support.CatalogDownloadImportService} 写入，不持久化。</p>
 */
@Component
public class CatalogDownloadJobRegistry {

    private final Map<String, CatalogDownloadJobProgressResult> jobs = new ConcurrentHashMap<>();

    /** 初始化任务进度（phase=waiting）。 */
    public void init(String jobId, int totalVersions) {
        jobs.put(jobId, new CatalogDownloadJobProgressResult()
            .setJobId(jobId)
            .setPhase("waiting")
            .setTotalVersions(totalVersions)
            .setProcessedVersions(0)
            .setParsedRows(0)
            .setUpsertedRows(0)
            .setPercent(0)
            .setDetailMessage("等待开始解压与入库")
            .setFinished(false)
            .setSuccess(false));
    }

    /** 增量更新任务进度字段。 */
    public void update(String jobId, Consumer<CatalogDownloadJobProgressResult> updater) {
        CatalogDownloadJobProgressResult current = jobs.get(jobId);
        if (current == null) {
            return;
        }
        updater.accept(current);
    }

    /** 标记任务成功完成并写入最终结果。 */
    public void complete(String jobId, CatalogDownloadResult result) {
        update(jobId, p -> {
            p.setPhase("done");
            p.setFinished(true);
            p.setSuccess(true);
            p.setPercent(100);
            p.setResult(result);
            p.setDetailMessage("全部 ZIP 已解压、解析并入库完成");
        });
    }

    /** 标记任务失败并记录错误信息。 */
    public void fail(String jobId, String message) {
        update(jobId, p -> {
            p.setPhase("failed");
            p.setFinished(true);
            p.setSuccess(false);
            p.setErrorMessage(message);
            p.setDetailMessage(message);
        });
    }

    /** 获取任务进度快照（防御性拷贝，不存在时返回 null）。 */
    public CatalogDownloadJobProgressResult get(String jobId) {
        CatalogDownloadJobProgressResult dto = jobs.get(jobId);
        if (dto == null) {
            return null;
        }
        return copyOf(dto);
    }

    private static CatalogDownloadJobProgressResult copyOf(CatalogDownloadJobProgressResult src) {
        return new CatalogDownloadJobProgressResult()
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
