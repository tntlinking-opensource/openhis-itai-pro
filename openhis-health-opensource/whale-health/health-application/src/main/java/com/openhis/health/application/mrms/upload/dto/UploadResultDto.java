/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 4401 上传结果 DTO。
 */
@Getter
@Setter
public class UploadResultDto {

    /** 上传状态码（SUCCESS/FAILED/SKIPPED 等）。 */
    private String statusCode;

    /** 本次上传日志主键（字符串序列化）。 */
    private String uploadLogId;

    /** 医保平台返回码（infcode）。 */
    private String infcode;

    /** 医保平台错误信息。 */
    private String errMsg;

    /** 结果组装时间。 */
    private LocalDateTime assembledAt;
}
