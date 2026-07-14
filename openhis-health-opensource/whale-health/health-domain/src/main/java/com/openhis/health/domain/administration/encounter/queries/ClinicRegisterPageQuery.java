/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 门诊挂号列表分页查询入参（查 {@code adm_encounter} 主表，AMB）。
 */
@Getter
@Setter
public class ClinicRegisterPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 挂号状态单值筛选。 */
    private String statusEnum;
    /** 排除的状态码列表（如统计当日有效挂号时排除 PLANNED）。 */
    private List<String> excludeStatusEnums;
    /** 挂号发生时刻窗下限（含）；语义 {@code coalesce(startTime, createdAt)}。 */
    private LocalDateTime startTime;
    /** 挂号发生时刻窗上限（含）。 */
    private LocalDateTime endTime;
    /** 患者主键（按人查历史挂号时传入）。 */
    private Long patientId;
}
