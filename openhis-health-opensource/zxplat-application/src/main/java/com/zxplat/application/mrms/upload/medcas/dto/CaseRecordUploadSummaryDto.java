/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.upload.medcas.dto;

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

    /** CHS 上传状态码（4401）。 */
    private String chsUploadStatusCode;

    /** CHS 上传失败原因摘要（4401）。 */
    private String chsUploadError;

    /**
     * 结算清单上传标志：{@code 0}/{@code 1}/{@code 2}（4101A/4102）。
     */
    private String settleUploadStatusCode;

    /** 结算清单最近上传错误摘要。 */
    private String settleUploadError;

    /** 4101A 平台清单流水号。 */
    private String setlListId;

    /** 医保人员编号 psn_no（结算上下文；未结算可空）。 */
    private String psnNo;

    /** 医保结算 ID setl_id（结算上下文；未结算可空）。 */
    private String setlId;
}
