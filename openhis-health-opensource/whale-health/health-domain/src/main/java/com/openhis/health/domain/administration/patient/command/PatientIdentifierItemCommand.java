/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.command;

import lombok.Getter;
import lombok.Setter;
import com.openhis.health.domain.share.enums.IdentifierState;

import java.time.LocalDateTime;

/**
 * 患者非身份证证件子命令。
 */
@Getter
@Setter
public class PatientIdentifierItemCommand {

    /**
     * 子实体标识。
     */
    private Long id;

    /**
     * 证件类型编码。
     */
    private String typeCode;

    /**
     * 证件号码。
     */
    private String identifierNo;

    /**
     * 标识状态编码。
     */
    private IdentifierState stateEnum;

    /**
     * 有效开始时间。
     */
    private LocalDateTime startTime;

    /**
     * 有效结束时间。
     */
    private LocalDateTime endTime;
}
