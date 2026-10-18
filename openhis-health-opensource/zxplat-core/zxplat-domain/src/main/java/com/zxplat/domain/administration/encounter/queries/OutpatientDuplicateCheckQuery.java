/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 门诊重复挂号校验查询入参。
 */
@Getter
@Setter
public class OutpatientDuplicateCheckQuery {

    /**
     * 患者标识。
     */
    private Long patientId;

    /**
     * 目标科室（组织机构）标识。
     */
    private Long organizationId;

    /**
     * 拟挂号业务自然日（与开立就诊的 occurredAt 日历日一致）；为空则按调用时刻的系统日期。
     */
    private LocalDate registrationDate;
}
