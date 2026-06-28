/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.list.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 病案检索列表行 DTO。
 */
@Getter
@Setter
public class CaseRecordListItemDto {

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 院内病案号。 */
    private String medcasno;

    /** 患者姓名。 */
    private String psnName;

    /** 住院号。 */
    private String iptNo;

    /** 性别字典码。 */
    private String gendCode;

    /** 入院时间。 */
    private LocalDateTime admTime;

    /** 出院时间。 */
    private LocalDateTime dscgTime;

    /** 入院科别名称。 */
    private String admCatyName;

    /** 出院科别名称。 */
    private String dscgCatyName;

    /** 主治医师姓名。 */
    private String chfdrName;

    /** 主诊断摘要。 */
    private String mainDiagName;

    /** 病案状态码。 */
    private String statusCode;

    /** 病案状态展示名。 */
    private String statusLabel;
}
