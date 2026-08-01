/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.devicecatalog.dto;

import lombok.Data;

import java.util.List;

/**
 * 耗材目录导入进度轮询响应。
 */
@Data
public class DeviceCatalogImportProgressResponse {

    /** 任务 ID。 */
    private String jobId;

    /** PENDING / RUNNING / SUCCEEDED / FAILED。 */
    private String status;

    /** PARSING / VALIDATING / WRITING / DONE。 */
    private String stage;

    /** 0～100。 */
    private Integer percent;

    /** 计划写入行数。 */
    private Integer total;

    /** 已处理行数。 */
    private Integer processed;

    /** 进度说明。 */
    private String message;

    /** 成功批次号。 */
    private String batchNo;

    /** 成功条数。 */
    private Integer successCount;

    /** 失败时错误标注文件 token。 */
    private String errorFileToken;

    /** 失败错误条数。 */
    private Integer errorCount;

    /** 错误摘要（最多若干条）。 */
    private List<String> errorMessages;
}
