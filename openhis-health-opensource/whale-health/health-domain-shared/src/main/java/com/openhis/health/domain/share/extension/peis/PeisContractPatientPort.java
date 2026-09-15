/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.extension.peis;

import java.util.List;

/**
 * 患者合同 / 合同类型只读 SPI（miniapp BFF → peis）。
 */
public interface PeisContractPatientPort {

    /**
     * 已启用合同类型字典。
     *
     * @return 类型列表
     */
    List<PeisContractTypeView> listEnabledContractTypes();

    /**
     * 患者当日有效团检合同（多份须手选）。
     *
     * @param patientId 患者主键
     * @return 有效合同
     */
    List<PeisValidContractView> listValidContractsForPatient(Long patientId);
}
