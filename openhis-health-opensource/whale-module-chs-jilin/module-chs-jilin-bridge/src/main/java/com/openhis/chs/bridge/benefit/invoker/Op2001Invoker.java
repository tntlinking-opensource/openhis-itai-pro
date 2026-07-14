/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.benefit.invoker;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.bridge.benefit.mapper.Op2001ResultMapper;
import com.openhis.chs.domain.outpatient.command.YbTreatmentCheckCommand;
import com.openhis.chs.domain.outpatient.result.YbTreatmentCheckResult;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.gateway.config.ContractConfig;
import com.openhis.chs.gateway.config.ContractConfigLoader;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.benefit.model.Op2001Input;
import com.openhis.chs.gateway.scheme.benefit.assembler.Op2001InputAssembler;
import com.openhis.chs.gateway.scheme.benefit.model.Op2001Output;
import com.openhis.chs.gateway.scheme.benefit.parser.Op2001OutputParser;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 【2001】人员待遇享受检查 Invoker：查询参保人在指定医疗类别下是否可享受医保待遇。
 *
 * <p>出站前校验 psnNo、insutype 等必填项，并从契约配置读取 {@code fixmedins_code} 组装入参。
 * 默认医疗类别为门诊（11），可通过 command 覆盖。</p>
 */
@Component
@RequiredArgsConstructor
public class Op2001Invoker {

    private final InsuranceGatewayClient gatewayClient;

    /**
     * 发起 2001 待遇检查并映射为 domain 结果。
     *
     * @param command 契约、人员编号、险种、参保地及可选 medType
     * @return 各基金类型的待遇享受标志与检查结果
     */
    public YbTreatmentCheckResult invoke(YbTreatmentCheckCommand command) {
        validateTreatmentCheck(command);
        ContractConfig config = requireContractConfig(command.getContractBusNo());
        InvokeContext invokeContext = InvokeContext.builder()
            .operatorId(String.valueOf(command.getOperatorId()))
            .insuplcAdmdvs(command.getInsuplcAdmdvs())
            .build();
        String medType = "11";
        if (isNotEmpty(command.getMedType())) {
            medType = command.getMedType().trim();
        }
        String begntime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Op2001Input input = Op2001InputAssembler.assemble(
            command.getPsnNo(),
            command.getInsutype(),
            config.getFixmedinsCode(),
            medType,
            begntime);
        ResultBody body = gatewayClient.invoke(
            command.getContractBusNo(), ChsInfNo.PERSON_TREATMENT_CHECK, input, invokeContext);
        Op2001Output output = Op2001OutputParser.requireOutput(body);
        return Op2001ResultMapper.fromOutput(output);
    }

    /**
     * 校验 2001 待遇检查命令必填项。
     *
     * @param command 待遇检查命令
     */
    private static void validateTreatmentCheck(YbTreatmentCheckCommand command) {
        if (command == null || command.getContractId() == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractId 不能为空");
        }
        if (isEmpty(command.getContractBusNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractBusNo 不能为空");
        }
        if (isEmpty(command.getPsnNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "psnNo 不能为空");
        }
        if (isEmpty(command.getInsutype())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "insutype 不能为空");
        }
    }

    /**
     * 按契约业务编号加载契约配置，不存在时抛错。
     *
     * @param contractBusNo 契约业务编号
     * @return 契约配置
     */
    private static ContractConfig requireContractConfig(String contractBusNo) {
        ContractConfig config = ContractConfigLoader.getByBusNo(contractBusNo);
        if (config == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_NOT_FOUND, "医保契约不存 " + contractBusNo);
        }
        return config;
    }
}
