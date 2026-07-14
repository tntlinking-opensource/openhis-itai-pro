/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.outpatient.invoker;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

import com.openhis.chs.bridge.outpatient.assembler.OutpatientSettleCommandAssembler;
import com.openhis.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.openhis.chs.domain.outpatient.command.OpRevokePlatformRegistrationCommand;
import com.openhis.chs.domain.outpatient.result.OpRevokePlatformRegistrationResult;
import com.openhis.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.openhis.chs.domain.outpatient.service.YbEncounterPlatformMapDomainService;
import com.openhis.chs.domain.outpatient.service.YbEncounterPlatformMapQueryService;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2202Input;
import com.openhis.chs.gateway.scheme.outpatient.assembler.Op2202InputAssembler;
import com.openhis.chs.gateway.scheme.outpatient.parser.Op2202OutputParser;
import com.openhis.chs.gateway.scheme.outpatient.assembler.OpOutpatientSettleInputAssembler;
import com.openhis.chs.gateway.support.ChsCurrentOperatorResolver;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 【2202】门诊挂号撤销 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；副作用：读/写 yb_encounter_platform_map。</p>
 */
@Component
@RequiredArgsConstructor
public class Op2202Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final YbEncounterPlatformMapQueryService encounterPlatformMapQueryService;
    private final YbEncounterPlatformMapDomainService encounterPlatformMapDomainService;
    private final ChsInsuranceContractReader contractReader;
    private final ChsCurrentOperatorResolver currentOperatorResolver;

    /**
     * 撤销门诊挂号登记并软删本地平台映射。
     *
     * @param command 含 encounterId、contractId、contractBusNo、operatorId 及可选 medType
     * @return 撤销结果（mdtrtId、是否已撤销）
     */
    public OpRevokePlatformRegistrationResult invoke(OpRevokePlatformRegistrationCommand command) {
        if (command == null || command.getEncounterId() == null || command.getContractId() == null) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "2202 缺少就诊或费用性质");
        }
        if (isEmpty(command.getContractBusNo())) {
            var contract = contractReader.requireContract(null, command.getContractId());
            command.setContractBusNo(contractReader.requireContractBusNo(contract));
        }
        if (command.getOperatorId() == null) {
            command.setOperatorId(currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录"));
        }
        if (command.getTenantId() == null) {
            command.setTenantId(TenantUtils.getCurrentTenantId());
        }
        Long tenantId = OutpatientSettleCommandAssembler.resolveTenantId(command.getTenantId());
        String medType = OpOutpatientSettleInputAssembler.resolveMedTypeForCommand(command.getMedType());
        Optional<YbEncounterPlatformMap> mapped = encounterPlatformMapQueryService
            .findOptionalByEncounterContractMedType(
                command.getEncounterId(), command.getContractId(), medType, tenantId);
        if (mapped.isEmpty() || isEmpty(mapped.get().getMdtrtId())) {
            OpRevokePlatformRegistrationResult skipped = new OpRevokePlatformRegistrationResult();
            skipped.setRevoked(false);
            return skipped;
        }
        YbEncounterPlatformMap row = mapped.get();
        String contractBusNo = command.getContractBusNo();
        if (isEmpty(contractBusNo)) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "2202 缺少 contractBusNo");
        }
        OutpatientSettleCommandAssembler.requireContractConfig(contractBusNo);
        InvokeContext invokeContext = OutpatientSettleCommandAssembler.buildInvokeContext(
            command.getOperatorId(), command.getInsuplcAdmdvs());
        Op2202Input input2202 = Op2202InputAssembler.assemble(
            row.getPsnNo(), row.getMdtrtId(), row.getIptOtpNo());
        ResultBody body2202 = gatewayClient.invoke(
            contractBusNo, ChsInfNo.OUTPATIENT_REG_REVOKE, input2202, invokeContext);
        Op2202OutputParser.requireSuccess(body2202);
        encounterPlatformMapDomainService.softDeleteAfterPlatformRevocation(
            command.getEncounterId(), command.getContractId(), medType, tenantId);
        OpRevokePlatformRegistrationResult result = new OpRevokePlatformRegistrationResult();
        result.setMdtrtId(row.getMdtrtId());
        result.setRevoked(true);
        return result;
    }
}
