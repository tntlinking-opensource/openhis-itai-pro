/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.benefit;

import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionIn.InsuranceCumulativeQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionIn.InsuranceOpspFilingQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionIn.InsurancePersonQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionIn.InsuranceTreatmentCheckExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceCumulativeQueryExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceOpspFilingEncounterSnapshot;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceOpspFilingQueryExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsurancePersonQueryExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceTreatmentCheckExtensionOut;

/**
 * 门诊挂号待遇确认前的第三方信息查询扩展点（1101/2001/5301/5206 及就医地区划解析）。
 *
 * <p><b>调用方</b>：{@code OutpatientRegistrationAppService} 待遇预览/确认流程。</p>
 * <p><b>吉林实现</b>：{@code InsuranceRegisterBenefitQueryExtensionImpl}（module-chs-jilin-bridge）；1101 经 {@code Op1101Invoker}。</p>
 */
public interface InsuranceRegisterBenefitQueryExtension {

    /** 是否已装配第三方查询 Module。 */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 费用性质是否走第三方查询。
     *
     * @param contractId {@code fin_contract.id}
     */
    default boolean supportsThirdPartyContract(Long contractId) {
        return false;
    }

    /**
     * 读卡获取参保人基本信息与险种列表（1101）。
     */
    default InsurancePersonQueryExtensionOut queryPerson(InsurancePersonQueryExtensionIn command) {
        throw new UnsupportedOperationException("第三方参保人信息查询未装配");
    }

    /**
     * 人员待遇享受检查（2001）。
     */
    default InsuranceTreatmentCheckExtensionOut checkTreatment(InsuranceTreatmentCheckExtensionIn command) {
        throw new UnsupportedOperationException("第三方待遇检查未装配");
    }

    /**
     * 慢特病备案查询（5301）。
     */
    default InsuranceOpspFilingQueryExtensionOut queryOpspFiling(InsuranceOpspFilingQueryExtensionIn command) {
        throw new UnsupportedOperationException("第三方慢特病备案查询未装配");
    }

    /**
     * 解析就诊侧 5301 所需人员编号与费用性质（医生站 IF-DOR-06B）。
     *
     * <p>实现可依次回退：平台就诊映射 → 预结算快照 → 读卡当日人员缓存；
     * 均不可用时返回 {@code null}（自费/未读卡）。</p>
     *
     * @param encounterId 就诊主键
     * @return 上下文；未启用或无可用人员编号时 {@code null}
     */
    default InsuranceOpspFilingEncounterSnapshot resolveOpspFilingEncounter(Long encounterId) {
        return null;
    }

    /**
     * 人员累计信息查询（5206）。
     */
    default InsuranceCumulativeQueryExtensionOut queryCumulative(InsuranceCumulativeQueryExtensionIn command) {
        throw new UnsupportedOperationException("第三方累计信息查询未装配");
    }

    /**
     * 解析费用性质对应的就医地医保区划（契约 adm_vs）。
     *
     * @param contractId 费用性质 ID
     * @return 就医地区划，未配置时 {@code null}
     */
    default String resolveTreatmentAreaCode(Long contractId) {
        return null;
    }
}
