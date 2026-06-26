/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.hisproxy;

import com.openhis.health.integration.insurance.benefit.InsuranceRegisterBenefitQueryExtension;
import com.openhis.health.integration.insurance.benefit.command.InsuranceTreatmentCheckCommand;
import com.openhis.health.integration.insurance.benefit.result.InsuranceTreatmentCheckResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 【2001】待遇检查 Bridge AppService（embedded / 独立 chs-server 共用 HTTP 入口）。
 *
 * <p>HTTP：{@code POST /api/app/insuranceTreatment/checkPersonTreatment}。</p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBean(InsuranceRegisterBenefitQueryExtension.class)
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsuranceTreatmentAppService {

    private final InsuranceRegisterBenefitQueryExtension registerBenefitQueryExtension;

    /**
     * 【2001】人员待遇享受检查。
     *
     * @param request 含 psnNo、insutype、contractId
     * @return 各基金待遇检查行
     */
    public InsuranceTreatmentCheckResult checkPersonTreatment(InsuranceTreatmentCheckCommand request) {
        return registerBenefitQueryExtension.checkTreatment(request);
    }
}
