/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.activitydefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 收费展示信息快照查询模型。
 *
 * <p>注意：该快照仅用于详情展示；计费事实源仍以收费域定价行为准。</p>
 */
@Getter
@Setter
public class PricingSnapshotQueryModel {
    /** 展示价（目录侧冗余/快照字段，若未落库则可为空）。 */
    private String standardPriceText;

    /** 价格版本或变更原因（可选）。 */
    private String reasonCode;
}
