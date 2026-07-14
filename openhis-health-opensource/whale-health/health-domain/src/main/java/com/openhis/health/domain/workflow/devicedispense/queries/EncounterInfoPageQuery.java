/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 就诊分页查询入参（按发放事实表过滤）。
 */
@Getter
@Setter
public class EncounterInfoPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;
}
