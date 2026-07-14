/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
