/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdefinition.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 药品目录维护页分页查询入参（读主表 {@code med_medication_definition}，替代产品读模型分页）。
 */
@Getter
@Setter
public class MedicationDefinitionCatalogPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 药品分类编码（可选）。 */
    private String categoryCode;

    /**
     * 重点药类型 code（可选，见 {@code KeyDrugType}）；空表示不限，非空时筛选对应标识为 {@code true} 的药品。
     */
    private String keyDrugType;
}
