/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 更正门诊挂号科室。
 *
 * <p>改的是挂号时选错的就诊科室，不改就诊状态、不改挂号医生、不重占号源。</p>
 */
@Getter
@Setter
public class CorrectOutpatientRegistrationDepartmentCommand {

    /** 门诊就诊主键。 */
    private Long encounterId;

    /** 目标门诊科室主键，写入就诊 {@code organization_id}。 */
    private Long departmentId;

    /**
     * 院点放宽同日同科多挂时为 true，领域侧跳过同日同科占名额断言。
     */
    private Boolean skipSameDaySameDeptDuplicateCheck;
}
