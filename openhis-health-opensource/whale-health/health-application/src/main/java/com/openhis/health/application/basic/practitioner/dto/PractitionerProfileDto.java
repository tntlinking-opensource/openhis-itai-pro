/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.practitioner.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 医务人员主档维护（岗位工作台）：签名图等。
 */
@Getter
@Setter
public class PractitionerProfileDto {
    /** 医务人员主键 {@code adm_practitioner.id}。 */
    private Long practitionerId;
    /** 医务人员姓名。 */
    private String name;
    /** 工号/业务编码。 */
    private String busNo;
    /** 在职状态枚举 code。 */
    private String statusEnum;
    /** 签名图 data URL 或纯 Base64；未维护时为 null */
    private String signatureImage;
    /** 是否已上传签名（列表/摘要场景可不返回大图） */
    private Boolean hasSignature;
}
