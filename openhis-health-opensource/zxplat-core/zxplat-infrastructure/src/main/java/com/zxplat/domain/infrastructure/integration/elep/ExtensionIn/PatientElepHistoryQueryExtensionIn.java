/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.elep.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 患者历史电子处方分页查询。
 *
 * <p>职责：医生站续方/查阅历史时按患者拉取平台侧已上传处方摘要列表。</p>
 */
@Data
@Accessors(chain = true)
public class PatientElepHistoryQueryExtensionIn {

    /** 患者主键。 */
    private Long patientId;

    /** 当前就诊主键（用于排除本就诊或上下文过滤，adapter 自定）。 */
    private Long currentEncounterId;

    /** 医保合同业务号。 */
    private String contractBusNo;

    /** 页码，从 0 起。 */
    private int page;

    /** 每页条数。 */
    private int pageSize;
}
