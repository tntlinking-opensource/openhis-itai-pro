/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.outpatient;

import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientRegistrationPlatformExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientRegistrationPlatformQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientRegistrationPlatformExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientRegistrationPlatformQueryExtensionOut;

import java.util.Collections;
import java.util.List;

/**
 * 门诊挂号 ↔ 第三方平台登记扩展点（与收费 Extension 分离，便于挂号阶段独立 2201）。
 *
 * <p><b>调用方</b>：{@code OutpatientRegistrationAppService#runInsuranceRegistrationIfNeeded}。</p>
 *
 * <p><b>吉林实现</b>：{@code OutpatientRegistrationInsuranceExtensionImpl}
 * → {@code ChsOutpatientSettlementClient#ensurePlatformRegistration} → 2201。</p>
 *
 * <p>挂号费医保预结算仍走 {@link OutpatientChargeInsuranceExtension#preSettle}（2204→2206），
 * 本 Extension 仅负责就诊在平台「登记存在」。</p>
 */
public interface OutpatientRegistrationInsuranceExtension {

    /** 是否已装配第三方挂号登记 Module。 */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 费用性质是否需平台挂号登记。
     *
     * @param contractId {@code fin_contract.id}
     * @return true 时在 preRegister 阶段调 {@link #ensurePlatformRegistration}
     */
    default boolean supportsThirdPartyContract(Long contractId) {
        return false;
    }

    /**
     * 确保平台就诊已登记（2201）；幂等，重复调用不重复挂号。
     *
     * @param command 就诊 id、读卡字段、medType（缺省 11）
     * @return mdtrt_id；未装配时抛异常
     * @throws UnsupportedOperationException 未装配 Module 时
     */
    default OutpatientRegistrationPlatformExtensionOut ensurePlatformRegistration(
        OutpatientRegistrationPlatformExtensionIn command) {
        throw new UnsupportedOperationException("第三方挂号登记未装配");
    }

    /**
     * 【2202】撤销平台门诊挂号登记；本地无映射时幂等跳过。
     *
     * @param command 就诊 id、费用性质、读卡字段（撤销后软删映射）
     * @return 撤销结果；未装配时抛异常
     */
    default OutpatientRegistrationPlatformExtensionOut revokePlatformRegistration(
        OutpatientRegistrationPlatformExtensionIn command) {
        throw new UnsupportedOperationException("第三方挂号登记未装配");
    }

    /**
     * 批量查询就诊是否已在平台挂号登记（2201）及关联预结算摘要。
     *
     * @param encounterIds 就诊主键列表
     * @param paymentDraftIdByEncounter 就诊 → 支付草稿（可为空 map）
     */
    default List<OutpatientRegistrationPlatformQueryExtensionOut> findPlatformSnapshots(
        OutpatientRegistrationPlatformQueryExtensionIn query) {
        return Collections.emptyList();
    }
}
