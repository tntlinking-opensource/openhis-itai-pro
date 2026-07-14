/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 上传日志 DTO。
 */
@Getter
@Setter
public class UploadLogDto {

    /** 上传日志主键（字符串序列化）。 */
    private String id;

    /** 病案主档主键（字符串序列化）。 */
    private String caseRecordId;

    /** 医保合约业务号。 */
    private String contractBusNo;

    /** 上传状态码。 */
    private String statusCode;

    /** 医保平台返回码（infcode）。 */
    private String infcode;

    /** 医保平台错误信息。 */
    private String errMsg;

    /** 请求报文摘要（脱敏/哈希）。 */
    private String requestDigest;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
