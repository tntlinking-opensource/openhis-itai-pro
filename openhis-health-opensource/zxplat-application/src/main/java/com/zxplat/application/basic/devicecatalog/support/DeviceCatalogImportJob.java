/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.devicecatalog.support;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 耗材目录异步导入任务（内存态）：供进度轮询；整批事务仍在写段一次提交。
 */
@Data
public class DeviceCatalogImportJob {

    /** 待执行。 */
    public static final String STATUS_PENDING = "PENDING";
    /** 执行中。 */
    public static final String STATUS_RUNNING = "RUNNING";
    /** 成功。 */
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    /** 失败（整批未落库或写段回滚）。 */
    public static final String STATUS_FAILED = "FAILED";

    /** 阶段：解析。 */
    public static final String STAGE_PARSING = "PARSING";
    /** 阶段：校验。 */
    public static final String STAGE_VALIDATING = "VALIDATING";
    /** 阶段：写入。 */
    public static final String STAGE_WRITING = "WRITING";
    /** 阶段：完成。 */
    public static final String STAGE_DONE = "DONE";

    /** 任务 ID。 */
    private String jobId;

    /** PENDING / RUNNING / SUCCEEDED / FAILED。 */
    private volatile String status = STATUS_PENDING;

    /** PARSING / VALIDATING / WRITING / DONE。 */
    private volatile String stage;

    /** 0～100。 */
    private volatile int percent;

    /** 计划写入行数（校验通过后才有意义）。 */
    private volatile int total;

    /** 已写入行数（写段中递增）。 */
    private volatile int processed;

    /** 人话进度说明。 */
    private volatile String message;

    /** 成功批次号。 */
    private volatile String batchNo;

    /** 成功条数。 */
    private volatile int successCount;

    /** 失败时错误标注文件 token。 */
    private volatile String errorFileToken;

    /** 失败时错误条数。 */
    private volatile int errorCount;

    /** 租户（异步线程还原）。 */
    private Long tenantId;

    /** 机构（异步写入直接绑定，避免会话丢失）。 */
    private Long orgId;

    /** 操作人。 */
    private Long operatorId;

    /** 原始文件名。 */
    private String fileName;

    /** 上传字节（任务持有至结束；大文件受常量上限约束）。 */
    private byte[] fileBytes;

    /** 分行错误摘要（最多保留若干条供前端展示）。 */
    private final List<String> errorMessages = new CopyOnWriteArrayList<>();

    /**
     * 复制错误摘要列表。
     *
     * @return 新列表
     */
    public List<String> copyErrorMessages() {
        return new ArrayList<>(errorMessages);
    }
}
