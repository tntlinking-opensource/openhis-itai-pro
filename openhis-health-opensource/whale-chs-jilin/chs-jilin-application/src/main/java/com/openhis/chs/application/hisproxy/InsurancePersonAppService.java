/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.hisproxy;

import com.openhis.health.integration.insurance.benefit.InsuranceRegisterBenefitQueryExtension;
import com.openhis.health.integration.insurance.benefit.command.InsurancePersonQueryCommand;
import com.openhis.health.integration.insurance.benefit.command.InsuranceTreatmentCheckCommand;
import com.openhis.health.integration.insurance.benefit.result.InsurancePersonQueryResult;
import com.openhis.health.integration.insurance.benefit.result.InsuranceTreatmentCheckResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 1101 读卡 Bridge AppService。
 *
 * <p>HTTP：{@code POST /api/app/insurancePerson/getPersonInfo}。</p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBean(InsuranceRegisterBenefitQueryExtension.class)
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsurancePersonAppService {

    private final InsuranceRegisterBenefitQueryExtension registerBenefitQueryExtension;

    /**
     * 【1101】读取参保人信息。
     *
     * @param request 读卡方式与凭证信息
     * @return 参保人基本信息
     */
    public InsurancePersonQueryResult getPersonInfo(InsurancePersonQueryCommand request) {
        return registerBenefitQueryExtension.queryPerson(request);
    }
}
