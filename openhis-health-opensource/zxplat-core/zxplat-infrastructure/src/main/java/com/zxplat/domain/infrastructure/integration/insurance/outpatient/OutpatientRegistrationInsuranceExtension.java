/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.outpatient;

import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientRegistrationPlatformExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientRegistrationPlatformQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientRegistrationPlatformExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientRegistrationPlatformQueryExtensionOut;

import java.util.Collections;
import java.util.List;

/**
 * 门诊挂号 ↔ 第三方平台登记扩展点（2201/2202，与收费 Extension 分离）。
 *
 * <p><b>目标态</b>：医保挂号确认阶段调用 {@link #ensurePlatformRegistration}（仅 2201，
 * {@code med_type=11} 写 map）；自费挂号不调。2203 在收费预结算链中按组调用。</p>
 *
 * <p><b>调用方</b>：{@code OutpatientRegistrationAppService} 预挂号建档 →
 * {@link #ensurePlatformRegistration}；作废/已结退号 → {@link #revokePlatformRegistration}。</p>
 *
 * <p><b>吉林实现</b>：{@code OutpatientRegistrationInsuranceExtensionImpl}
 * → {@code OpPlatformRegistrationOrchestrator}（仅 2201）。</p>
 */
public interface OutpatientRegistrationInsuranceExtension {

    /** 是否已装配第三方挂号登记 Module。 */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 费用性质是否需平台挂号登记/撤销。
     *
     * @param contractId {@code fin_contract.id}
     * @return true 时允许调用 ensure/revoke
     */
    default boolean supportsThirdPartyContract(Long contractId) {
        return false;
    }

    /**
     * 确保平台就诊已登记（2201）；幂等。医保挂号确认主路径调用；自费挂号不调。
     *
     * @param command 就诊 id、读卡字段、medType（挂号建档用 11；缺省实现侧落 11）
     * @return mdtrt_id；未装配时抛异常
     * @throws UnsupportedOperationException 未装配 Module 时
     */
    default OutpatientRegistrationPlatformExtensionOut ensurePlatformRegistration(
        OutpatientRegistrationPlatformExtensionIn command) {
        throw new UnsupportedOperationException("第三方挂号登记未装配");
    }

    /**
     * 【2202】撤销平台门诊挂号登记；本地无对应 med_type 映射时幂等跳过。
     *
     * @param command 就诊 id、费用性质、medType（挂号侧须传 12）
     * @return 撤销结果（有撤则带回 mdtrt_id）；未装配时抛异常
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
