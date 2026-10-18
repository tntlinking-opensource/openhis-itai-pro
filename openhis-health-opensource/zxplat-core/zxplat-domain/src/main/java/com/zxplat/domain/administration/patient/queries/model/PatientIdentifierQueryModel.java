/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.patient.queries.model;

import lombok.Getter;
import lombok.Setter;
import com.zxplat.domain.share.enums.IdentifierState;

import java.time.LocalDateTime;

/**
 * 患者证件查询模型。
 */
@Getter
@Setter
public class PatientIdentifierQueryModel {

    /** 子实体标识。 */
    private Long id;
    /** 证件类型编码。 */
    private String typeCode;
    /** 证件号码。 */
    private String identifierNo;
    /** 标识状态编码。 */
    private IdentifierState stateEnum;
    /** 有效开始时间。 */
    private LocalDateTime startTime;
    /** 有效结束时间。 */
    private LocalDateTime endTime;
}
