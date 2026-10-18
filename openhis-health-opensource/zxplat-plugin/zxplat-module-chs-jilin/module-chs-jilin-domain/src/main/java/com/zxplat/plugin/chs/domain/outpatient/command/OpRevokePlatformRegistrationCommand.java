/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.outpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2202 门诊挂号撤销命令 */
@Data
@Accessors(chain = true)
public class OpRevokePlatformRegistrationCommand {

    private Long encounterId;
    private Long contractId;
    private String medType;
    private String contractBusNo;
    private String insuplcAdmdvs;

    /**
     * 为 true 时参保地必须来自原挂号人员缓存，空则拒绝，禁止回落医院就医地。
     */
    private Boolean idCardAsMdtrtCert;

    /**
     * 为 true 且有效映射已不在时，用最近一条已软删、仍带 mdtrt_id 的登记调用 2202。
     * 普通退号保持 false，避免把已撤销的挂号再打一次平台。
     */
    private Boolean allowDeletedMap;

    private Long operatorId;
    private Long tenantId;
}
