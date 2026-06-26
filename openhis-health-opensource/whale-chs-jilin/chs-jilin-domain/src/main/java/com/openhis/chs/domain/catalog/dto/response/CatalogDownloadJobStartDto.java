/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** 目录 ZIP 拉取完成后的异步入库任务启动结果。 */
@Data
@Accessors(chain = true)
@Schema(description = "医保目录异步入库任务启动结果")
public class CatalogDownloadJobStartDto {

    @Schema(description = "入库任务 ID，用于轮询进度")
    private String jobId;

    @Schema(description = "接口号 infno")
    private String infno;

    @Schema(description = "待处理 ZIP 包数")
    private int totalVersions;

    @Schema(description = "说明")
    private String message;
}
