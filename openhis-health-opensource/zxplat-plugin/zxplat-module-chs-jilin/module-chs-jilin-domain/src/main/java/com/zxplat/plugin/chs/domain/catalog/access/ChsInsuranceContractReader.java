/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.access;

import com.zxplat.plugin.chs.domain.contract.ChsContractSnapshot;

/** 解析医保业务所绑定的费用性质契约 */
public interface ChsInsuranceContractReader {

    ChsContractSnapshot requireContract(String contractBusNo, Long contractId);

    String requireContractBusNo(ChsContractSnapshot contract);

    /** 判断费用性质是否为医保契约（用于 HIS 扩展点是否走医保流程） */
    boolean isInsuranceContract(Long contractId);
}
