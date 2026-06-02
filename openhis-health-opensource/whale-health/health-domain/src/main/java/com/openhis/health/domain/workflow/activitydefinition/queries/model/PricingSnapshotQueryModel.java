/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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

