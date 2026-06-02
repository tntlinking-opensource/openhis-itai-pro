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

import java.util.List;

/**
 * 诊疗项目目录详情查询模型。
 */
@Getter
@Setter
public class ActivityDefinitionDetailQueryModel {
    /** 基础信息。 */
    private ActivityDefinitionBaseQueryModel baseInfo;
    /** children。 */
    private List<ActivityDefinitionChildQueryModel> children;
    /** 计价snapshot。 */
    private PricingSnapshotQueryModel pricingSnapshot;
}

