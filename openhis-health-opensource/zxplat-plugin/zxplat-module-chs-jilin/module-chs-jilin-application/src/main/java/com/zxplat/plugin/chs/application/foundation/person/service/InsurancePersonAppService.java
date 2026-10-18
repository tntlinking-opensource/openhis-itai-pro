/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.foundation.person.service;

import com.zxplat.plugin.chs.application.foundation.person.request.YbPersonInfoRequest;
import com.zxplat.plugin.chs.application.foundation.person.mapper.InsurancePersonAppMapper;
import com.zxplat.plugin.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.zxplat.plugin.chs.domain.outpatient.access.Op1101QueryInvoker;
import com.zxplat.plugin.chs.domain.outpatient.result.YbPersonInfoResult;
import com.zxplat.framework.core.application.session.CurrentOperatorResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 1101 读卡 UI 应用服务（{@code POST /api/app/insurancePerson/getPersonInfo}）。
 * <p>职责：将 UI 入参转为领域命令并调用 1101 人员信息查询。</p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
public class InsurancePersonAppService {

    private final Op1101QueryInvoker op1101QueryInvoker;
    private final ChsInsuranceContractReader contractReader;
    private final CurrentOperatorResolver currentOperatorResolver;

    /**
     * 调用 1101 接口查询参保人员信息。
     */
    public YbPersonInfoResult getPersonInfo(YbPersonInfoRequest request) {
        Long operatorId = currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录");
        return op1101QueryInvoker.invoke(
                InsurancePersonAppMapper.toPersonQueryCommand(request, contractReader, operatorId));
    }
}
