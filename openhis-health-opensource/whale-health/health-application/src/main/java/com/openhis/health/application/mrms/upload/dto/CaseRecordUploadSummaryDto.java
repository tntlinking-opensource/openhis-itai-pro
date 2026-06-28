/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.upload.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 医保办上传列表摘要 DTO。
 */
@Getter
@Setter
public class CaseRecordUploadSummaryDto {

    /** 病案主档主键（字符串序列化）。 */
    private String caseRecordId;

    /** 院内病案号。 */
    private String medcasno;

    /** 患者姓名。 */
    private String psnName;

    /** 出院时间。 */
    private LocalDateTime dscgTime;

    /** 归档时间。 */
    private LocalDateTime archivedAt;

    /** CHS 上传状态码。 */
    private String chsUploadStatusCode;

    /** CHS 上传失败原因摘要。 */
    private String chsUploadError;
}
