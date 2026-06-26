/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.prescription.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 当前就诊电子处方列表查询。 */
@Data
@Accessors(chain = true)
public class ElepEncounterListQuery {

    private Long encounterId;
}
