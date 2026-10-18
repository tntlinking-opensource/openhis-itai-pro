/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.queries;

import com.zxplat.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 挂号单分页查询入参（查 {@code adm_encounter} 主表，AMB）。
 */
@Getter
@Setter
public class RegistrationSlipPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 就诊主键（按单张挂号单精确查）。 */
    private Long encounterId;
    /** 挂号单号/ slip 业务号。 */
    private String slipNo;
    /** 患者主键。 */
    private Long patientId;
    /** 挂号状态持久化码。 */
    private String statusEnum;
    /** 挂号发生时刻窗下限（含）。 */
    private LocalDateTime startTime;
    /** 挂号发生时刻窗上限（含）。 */
    private LocalDateTime endTime;
}
