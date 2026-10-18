/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.outpatient.invoker;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;

import com.zxplat.plugin.chs.bridge.outpatient.assembler.OutpatientSettleCommandAssembler;
import com.zxplat.plugin.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.zxplat.plugin.chs.domain.outpatient.command.OpRevokePlatformRegistrationCommand;
import com.zxplat.plugin.chs.domain.outpatient.result.OpRevokePlatformRegistrationResult;
import com.zxplat.plugin.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.zxplat.plugin.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.zxplat.plugin.chs.domain.outpatient.entity.YbPersonCache;
import com.zxplat.plugin.chs.domain.outpatient.service.YbClinicPreSettleQueryService;
import com.zxplat.plugin.chs.domain.outpatient.service.YbEncounterPlatformMapDomainService;
import com.zxplat.plugin.chs.domain.outpatient.service.YbEncounterPlatformMapQueryService;
import com.zxplat.plugin.chs.domain.outpatient.service.YbPersonCacheDomainService;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.gateway.observability.ChsInvokeLogContext;
import com.zxplat.plugin.chs.gateway.observability.ChsInvokeLogContextHolder;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.outpatient.model.Op2202Input;
import com.zxplat.plugin.chs.gateway.scheme.outpatient.assembler.Op2202InputAssembler;
import com.zxplat.plugin.chs.gateway.scheme.outpatient.parser.Op2202OutputParser;
import com.zxplat.plugin.chs.gateway.scheme.outpatient.assembler.OpOutpatientSettleInputAssembler;
import com.zxplat.plugin.chs.gateway.support.ChsCurrentOperatorResolver;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
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
    private final YbPersonCacheDomainService personCacheDomainService;
    private final YbClinicPreSettleQueryService clinicPreSettleQueryService;

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
        if (mapped.isEmpty() && Boolean.TRUE.equals(command.getAllowDeletedMap())) {
            mapped = encounterPlatformMapQueryService.findLatestDeletedByEncounterContractMedType(
                command.getEncounterId(), command.getContractId(), medType, tenantId);
        }
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
        if (isEmpty(command.getInsuplcAdmdvs())) {
            this.fillRevokeInsuplcFromPersonCache(command, row.getPsnNo(), tenantId);
        }
        if (isEmpty(command.getInsuplcAdmdvs())) {
            this.fillRevokeInsuplcFromPreSettle(command, tenantId);
        }
        if (Boolean.TRUE.equals(command.getIdCardAsMdtrtCert()) && isEmpty(command.getInsuplcAdmdvs())) {
            throw new BusinessException(
                ChsErrorCodes.PLATFORM_FAILED,
                "无法取得原挂号参保地，请让患者重新读卡后再修改科室");
        }
        InvokeContext invokeContext = OutpatientSettleCommandAssembler.buildInvokeContext(
            command.getOperatorId(), command.getInsuplcAdmdvs());
        Op2202Input input2202 = Op2202InputAssembler.assemble(
            row.getPsnNo(), row.getMdtrtId(), row.getIptOtpNo());
        ChsInvokeLogContextHolder.set(new ChsInvokeLogContext()
            .setContractId(command.getContractId())
            .setEncounterId(command.getEncounterId())
            .setTenantId(tenantId));
        ResultBody body2202;
        try {
            body2202 = gatewayClient.invoke(
                contractBusNo, ChsInfNo.OUTPATIENT_REG_REVOKE, input2202, invokeContext);
        } finally {
            ChsInvokeLogContextHolder.clear();
        }
        Op2202OutputParser.requireSuccess(body2202);
        encounterPlatformMapDomainService.softDeleteAfterPlatformRevocation(
            command.getEncounterId(), command.getContractId(), medType, tenantId);
        OpRevokePlatformRegistrationResult result = new OpRevokePlatformRegistrationResult();
        result.setMdtrtId(row.getMdtrtId());
        result.setRevoked(true);
        return result;
    }

    /**
     * 当日人员缓存已过期时，用挂号预结算上的参保地，避免省医保回落成 229900。
     *
     * @param command  2202 命令
     * @param tenantId 租户主键
     */
    private void fillRevokeInsuplcFromPreSettle(
        OpRevokePlatformRegistrationCommand command, Long tenantId) {
        if (command.getEncounterId() == null) {
            return;
        }
        List<YbClinicPreSettle> rows = clinicPreSettleQueryService.listByEncounterIds(
            List.of(command.getEncounterId()), tenantId);
        String matched = null;
        String any = null;
        for (YbClinicPreSettle row : rows) {
            if (row == null || isEmpty(row.getInsuplcAdmdvs())) {
                continue;
            }
            String admdvs = row.getInsuplcAdmdvs().trim();
            if (any == null) {
                any = admdvs;
            }
            if (command.getContractId() != null && command.getContractId().equals(row.getContractId())) {
                matched = admdvs;
                break;
            }
        }
        if (matched != null) {
            command.setInsuplcAdmdvs(matched);
            return;
        }
        if (any != null) {
            command.setInsuplcAdmdvs(any);
        }
    }

    /**
     * 撤销报文头参保地为空时，用当日人员缓存补上，避免回落医院就医地。
     *
     * @param command  2202 命令
     * @param psnNo    平台映射上的人员编号
     * @param tenantId 租户主键
     */
    private void fillRevokeInsuplcFromPersonCache(
        OpRevokePlatformRegistrationCommand command, String psnNo, Long tenantId) {
        if (command.getContractId() == null || isEmpty(psnNo)) {
            return;
        }
        Optional<YbPersonCache> cache = personCacheDomainService.findValidByContractIdAndPsnNo(
            command.getContractId(), psnNo.trim(), tenantId);
        if (cache.isEmpty()) {
            return;
        }
        YbPersonCache row = cache.get();
        if (!isEmpty(row.getInsuplcAdmdvs())) {
            command.setInsuplcAdmdvs(row.getInsuplcAdmdvs().trim());
        }
    }
}
