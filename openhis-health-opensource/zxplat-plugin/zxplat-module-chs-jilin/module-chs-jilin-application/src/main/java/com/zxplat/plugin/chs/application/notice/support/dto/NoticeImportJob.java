/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.support.dto;

import com.zxplat.plugin.chs.application.notice.dto.NoticeScanImportResultDto;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 异步确认入库任务（内存态）：支持进度轮询与中断后续跑剩余包。
 */
@Data
public class NoticeImportJob {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    /** 任务 ID。 */
    private String jobId;

    /** PENDING / RUNNING / SUCCEEDED / FAILED。 */
    private volatile String status = STATUS_PENDING;

    /** 计划入库总数。 */
    private volatile int total;

    /** 已处理个数（含成功/跳过/失败）。 */
    private volatile int processed;

    /** 当前正在入库的子目录。 */
    private volatile String currentPackDir;

    /** 阶段或失败提示。 */
    private volatile String message;

    private volatile int importedCount;
    private volatile int skippedCount;
    private volatile int failedCount;

    /** 扫描会话令牌（续跑时仍须有效）。 */
    private String scanToken;

    /** 扫描根目录快照。 */
    private String scanRoot;

    private Long contractId;
    private boolean force;
    private Long tenantId;

    /**
     * 尚待处理的子目录名队列（线程安全）。
     * <p>每完成一个包就从队头移除；续跑时从未完成队列继续。</p>
     */
    private final List<String> remainingPackDirs = new CopyOnWriteArrayList<>();

    /** 已产生的逐包结果。 */
    private final List<NoticeScanImportResultDto> results = new CopyOnWriteArrayList<>();

    /**
     * 复制结果列表供 API 返回。
     *
     * @return 新列表
     */
    public List<NoticeScanImportResultDto> copyResults() {
        return new ArrayList<>(results);
    }

    /**
     * 剩余未处理个数。
     *
     * @return 剩余条数
     */
    public int remainingCount() {
        return remainingPackDirs.size();
    }
}
