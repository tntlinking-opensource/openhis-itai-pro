/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.patient.dto;

import lombok.Data;

/**
 * 患者其他证件/标识信息项 DTO（单条，对应 adm_patient_identifier 表一行）。
 * 用于患者详情返回与编辑回显，身份证以外的证件（如港澳台通行证、护照等）在此维护。
 */
@Data
public class PatientIdentifierItemDto {

    /** 主键（编辑回显时有值，新增时无） */
    private Long id;
    /** 标识用途：1常用 2正式 3临时 4次要 5旧，见 IdentifierUse */
    private String useEnum;
    /** 证件类型编码（来自字典 psn_cert_type / IdentityDocumentType，不含身份证） */
    private String typeCode;
    /** 证件/标识号 */
    private String identifierNo;
    /** 有效开始时间，格式 yyyy-MM-dd HH:mm:ss */
    private String startTime;
    /** 有效结束时间，格式 yyyy-MM-dd HH:mm:ss */
    private String endTime;
}
