/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.medcas;

import com.alibaba.fastjson2.JSON;
import com.openhis.chs.api.ChsInfNo;
import com.openhis.chs.api.InsuranceGatewayClient;
import com.openhis.chs.api.InvokeContext;
import com.openhis.chs.api.ResultBody;
import com.openhis.health.integration.insurance.medcas.command.MedcasHomepageUploadCommand;
import com.openhis.health.integration.insurance.medcas.result.MedcasUploadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * {@link MedcasHomepageUploadExtension} 实现（委托 CHS 4401 网关）。
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(InsuranceGatewayClient.class)
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class MedcasHomepageUploadExtensionImpl implements MedcasHomepageUploadExtension {

    private final InsuranceGatewayClient gatewayClient;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public MedcasUploadResult upload(MedcasHomepageUploadCommand command) {
        Object input = JSON.parse(command.getInputJson());
        InvokeContext context = InvokeContext.builder()
                .operatorId(command.getOperatorId() != null ? String.valueOf(command.getOperatorId()) : null)
                .build();
        ResultBody body = gatewayClient.invoke(
                command.getContractBusNo(), ChsInfNo.MEDCAS_HOMEPAGE, input, context);
        MedcasUploadResult result = new MedcasUploadResult();
        if (body == null) {
            result.setSuccess(false);
            result.setErrMsg("医保平台无返回");
            return result;
        }
        result.setInfcode(body.getInfcode());
        result.setErrMsg(body.getErrMsg());
        result.setWarnMsg(body.getWarnMsg());
        result.setOutput(body.getOutput());
        result.setSuccess("0".equals(trimToNull(body.getInfcode())));
        return result;
    }
}
