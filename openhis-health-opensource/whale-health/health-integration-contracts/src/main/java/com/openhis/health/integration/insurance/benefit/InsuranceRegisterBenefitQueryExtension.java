/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.benefit;

import com.openhis.health.integration.insurance.benefit.command.InsuranceCumulativeQueryCommand;
import com.openhis.health.integration.insurance.benefit.command.InsuranceOpspFilingQueryCommand;
import com.openhis.health.integration.insurance.benefit.command.InsurancePersonQueryCommand;
import com.openhis.health.integration.insurance.benefit.command.InsuranceTreatmentCheckCommand;
import com.openhis.health.integration.insurance.benefit.result.InsuranceCumulativeQueryResult;
import com.openhis.health.integration.insurance.benefit.result.InsuranceOpspFilingQueryResult;
import com.openhis.health.integration.insurance.benefit.result.InsurancePersonQueryResult;
import com.openhis.health.integration.insurance.benefit.result.InsuranceTreatmentCheckResult;

/**
 * 门诊挂号待遇确认前的第三方信息查询扩展点（1101/2001/5301/5206 及就医地区划解析）。
 *
 * <p><b>调用方</b>：{@code OutpatientRegistrationAppService} 待遇预览/确认流程。</p>
 * <p><b>吉林实现</b>：{@code InsuranceRegisterBenefitQueryExtensionImpl}（chs-jilin-adapter）。</p>
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
    default InsurancePersonQueryResult queryPerson(InsurancePersonQueryCommand command) {
        throw new UnsupportedOperationException("第三方参保人信息查询未装配");
    }

    /**
     * 人员待遇享受检查（2001）。
     */
    default InsuranceTreatmentCheckResult checkTreatment(InsuranceTreatmentCheckCommand command) {
        throw new UnsupportedOperationException("第三方待遇检查未装配");
    }

    /**
     * 慢特病备案查询（5301）。
     */
    default InsuranceOpspFilingQueryResult queryOpspFiling(InsuranceOpspFilingQueryCommand command) {
        throw new UnsupportedOperationException("第三方慢特病备案查询未装配");
    }

    /**
     * 人员累计信息查询（5206）。
     */
    default InsuranceCumulativeQueryResult queryCumulative(InsuranceCumulativeQueryCommand command) {
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
