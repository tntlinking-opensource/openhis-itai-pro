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
     * 重点药类型 code（可选）；仅认基药/监控/集采/抗生素；空表示不限。麻/精见 {@link #medicinePropertyCode}。
     */
    private String keyDrugType;

    /**
     * 药品性质 code（可选，NARCOTIC/PSYCH_ONE/PSYCH_TWO）；空表示不限。
     * 与 {@link #keyDrugType} 同时非空时按 AND 收窄。
     */
    private String medicinePropertyCode;

    /**
     * 发布状态 code（可选，见 {@link com.openhis.health.domain.share.enums.PublicationStatus}）；空表示不限。
     */
    private String statusEnum;
}
