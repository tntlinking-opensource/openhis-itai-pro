/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.command;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 加载电子处方就诊上下文（只读 HIS + 挂号 map(11)）。
 */
@Data
@Accessors(chain = true)
public class ElepEncounterContextQuery {

    /** 当前门诊就诊主键。 */
    private Long encounterId;

    /** 患者主键，须与就诊归属一致。 */
    private Long patientId;

    /**
     * 医保契约 bus_no（可空）。
     *
     * <p>优先用挂号 map 上的契约回填；仅在 map 没有 contract_id 时作兜底。</p>
     */
    private String contractBusNo;
}
