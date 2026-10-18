/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.observation.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 取消审核时吊销由该 PACS 报告投影出的 Observation（status→cancelled）。
 */
@Getter
@Setter
public class RevokeFromPacsSummaryCommand {

    /** PACS 报告主键（= Observation.sourceObservationId）。 */
    private Long pacsReportId;

    /** 操作人。 */
    private Long operatorId;
}
