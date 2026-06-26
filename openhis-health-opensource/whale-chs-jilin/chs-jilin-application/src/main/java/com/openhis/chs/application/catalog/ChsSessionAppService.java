/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.api.InvokeContext;
import com.openhis.chs.api.InsuranceGatewayClient;
import com.openhis.chs.domain.ChsErrorCodes;
import com.openhis.chs.domain.catalog.dto.request.SignInRequest;
import com.openhis.chs.domain.catalog.facade.ChsSessionOperations;
import com.openhis.chs.domain.catalog.port.ChsContractSnapshot;
import com.openhis.chs.domain.catalog.port.ChsInsuranceContractPort;
import com.openhis.chs.application.gateway.session.ChsSignCredentialResolver;
import com.openhis.chs.application.gateway.support.ChsCurrentOperatorResolver;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 医保会话应用服务：9001 签到及 sign_no 获取。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsSessionAppService implements ChsSessionOperations {

    private final InsuranceGatewayClient gatewayClient;
    private final ChsInsuranceContractPort contractPort;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    private final ChsSignCredentialResolver signCredentialResolver;

    /**
     * 【9001】签到并返回 sign_no；未签到时后续 1312/3301 等平台交易将被拒绝。
     *
     * @param request 契约 bus_no、可选 MAC/IP
     * @return 平台 sign_no
     * @throws BusinessException 契约无效、未登录或平台拒绝时
     */
    @Override
    public String signIn(SignInRequest request) {
        // Gate：契约须存在且可解析 bus_no，否则无法路由网关
        ChsContractSnapshot contract =
                contractPort.requireContract(request.getContractBusNo(), request.getContractId());
        String busNo = contractPort.requireContractBusNo(contract);
        // Pre：嵌入 HIS 须已登录；独立 chs-server 使用固定操作人
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录后再签到");
        InvokeContext context = buildInvokeContext(request, operatorId);
        // Cmd：调网关 9001；平台异常转为业务错误便于前端展示
        try {
            String signNo = gatewayClient.signIn(busNo, context);
            if (StringUtils.isBlank(signNo)) {
                throw new BusinessException(ChsErrorCodes.SIGN_IN_FAILED, "9001 签到失败：未返回 sign_no");
            }
            return signNo;
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new BusinessException(ChsErrorCodes.SIGN_IN_FAILED, ex.getMessage());
        }
    }

    private InvokeContext buildInvokeContext(SignInRequest request, Long operatorId) {
        String operatorIdText;
        if (operatorId != null) {
            operatorIdText = String.valueOf(operatorId);
        } else {
            operatorIdText = null;
        }
        InvokeContext partial = InvokeContext.builder()
            .operatorId(operatorIdText)
            .signMac(trimToNull(request.getMac()))
            .signIp(trimToNull(request.getIp()))
            .build();
        return InvokeContext.builder()
            .operatorId(signCredentialResolver.resolveOperatorId(partial))
            .operatorName(signCredentialResolver.resolveOperatorName(partial))
            .signMac(partial.getSignMac())
            .signIp(partial.getSignIp())
            .build();
    }
}