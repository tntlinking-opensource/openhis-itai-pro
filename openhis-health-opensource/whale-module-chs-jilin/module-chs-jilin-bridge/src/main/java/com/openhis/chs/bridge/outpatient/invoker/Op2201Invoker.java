/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.outpatient.invoker;

import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.openhis.chs.domain.outpatient.entity.YbPersonCache;
import com.openhis.chs.domain.outpatient.service.YbEncounterPlatformMapDomainService;
import com.openhis.chs.domain.outpatient.service.YbEncounterPlatformMapQueryService;
import com.openhis.chs.domain.outpatient.service.YbPersonCacheDomainService;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.domain.share.enums.MdtrtCertType;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2201Input;
import com.openhis.chs.gateway.scheme.outpatient.assembler.Op2201InputAssembler;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2201Output;
import com.openhis.chs.gateway.scheme.outpatient.parser.Op2201OutputParser;
import com.openhis.chs.gateway.scheme.outpatient.assembler.OpOutpatientSettleInputAssembler;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 【2201】门诊挂号登记 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；副作用：读/写 yb_encounter_platform_map。</p>
 */
@Component
@RequiredArgsConstructor
public class Op2201Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final YbEncounterPlatformMapQueryService encounterPlatformMapQueryService;
    private final YbEncounterPlatformMapDomainService encounterPlatformMapDomainService;
    private final YbPersonCacheDomainService personCacheDomainService;

    /**
     * 查询本地就诊平台映射，不存在时调用 2201 登记并落库。
     *
     * <p>map 命中时：若上下文缺少 {@code psnNo}，用 map 行回填，供后续 3101 {@code patn_id}。</p>
     *
     * @param contractBusNo 医保契约 bus_no
     * @param context       门诊预结算上下文（就诊、费用性质、人员编号等）
     * @param invokeContext 操作员与签到上下文
     * @param tenantId      租户 ID
     * @return 医保平台就诊 ID（mdtrt_id）
     */
    public String resolveOrRegister(
            String contractBusNo,
            OpPreSettleCommand context,
            InvokeContext invokeContext,
            Long tenantId) {
        String medType = OpOutpatientSettleInputAssembler.resolveMedTypeForContext(context);
        Optional<YbEncounterPlatformMap> mappedOpt = encounterPlatformMapQueryService
            .findOptionalByEncounterContractMedType(
                context.getEncounterId(), context.getContractId(), medType, tenantId);
        if (mappedOpt.isPresent()) {
            YbEncounterPlatformMap mapped = mappedOpt.get();
            // Pre：签发门禁常不传 psnNo，map 命中须回填，否则 3101 patn_id 为空
            if (StringUtils.isEmpty(context.getPsnNo()) && StringUtils.isNotEmpty(mapped.getPsnNo())) {
                context.setPsnNo(mapped.getPsnNo().trim());
            }
            return mapped.getMdtrtId();
        }
        // Pre：读卡得到哪种凭证用哪种（01/02/03），禁止强制改写
        this.resolveMdtrtCert(context, tenantId);
        Op2201Input input2201 = Op2201InputAssembler.assemble(context);
        ResultBody body2201 = gatewayClient.invoke(
            contractBusNo, ChsInfNo.OUTPATIENT_REG, input2201, invokeContext);
        Op2201Output output2201 = Op2201OutputParser.requireResult(body2201);
        String mdtrtId = Op2201OutputParser.resolveMdtrtId(output2201);
        encounterPlatformMapDomainService.saveAfterPlatformRegistration(
            context.getEncounterId(),
            context.getContractId(),
            medType,
            mdtrtId.trim(),
            context.getPsnNo(),
            context.getEncounterBusNo(),
            tenantId);
        return mdtrtId.trim();
    }

    /**
     * 仅查询已落库的平台就诊号（不调 2201）。
     *
     * @param context  须含 encounterId、contractId、medType（可空则默认 11）
     * @param tenantId 租户
     * @return map 行；未建档为空
     */
    public Optional<YbEncounterPlatformMap> findMapped(
            OpPreSettleCommand context, Long tenantId) {
        if (context == null || context.getEncounterId() == null || context.getContractId() == null) {
            return Optional.empty();
        }
        String medType = OpOutpatientSettleInputAssembler.resolveMedTypeForContext(context);
        return encounterPlatformMapQueryService.findOptionalByEncounterContractMedType(
            context.getEncounterId(), context.getContractId(), medType, tenantId);
    }

    /**
     * 解析 2201 就诊凭证：上下文原样（01/02/03）优先，否则取人缓存读卡主凭证；禁止静默伪造。
     *
     * @param context  门诊预结算上下文
     * @param tenantId 租户 ID
     */
    private void resolveMdtrtCert(OpPreSettleCommand context, Long tenantId) {
        if (this.hasUsableReadCert(context)) {
            context.setMdtrtCertType(context.getMdtrtCertType().trim());
            context.setMdtrtCertNo(context.getMdtrtCertNo().trim());
            return;
        }
        String idCard = StringUtils.isEmpty(context.getPatientCertNo())
            ? null
            : context.getPatientCertNo().trim().toUpperCase();
        if (StringUtils.isNotEmpty(idCard) && context.getContractId() != null) {
            Optional<YbPersonCache> primaryOpt = personCacheDomainService
                .findValidPrimaryReadCertByContractIdAndIdCard(
                    context.getContractId(), idCard, tenantId);
            if (primaryOpt.isPresent()) {
                YbPersonCache primary = primaryOpt.get();
                context.setMdtrtCertType(primary.getMdtrtCertType().trim());
                context.setMdtrtCertNo(primary.getMdtrtCertNo().trim());
                return;
            }
        }
        throw new BusinessException(
            ChsErrorCodes.PLATFORM_FAILED,
            "2201 缺少读卡就诊凭证，请重新读卡（医保码/身份证/医保卡）后再试");
    }

    /**
     * 上下文是否已带可用读卡凭证（01/02/03 + 非空凭证号）。
     *
     * @param context 门诊预结算上下文
     * @return 可用则 true
     */
    private boolean hasUsableReadCert(OpPreSettleCommand context) {
        String type = StringUtils.isEmpty(context.getMdtrtCertType())
            ? null
            : context.getMdtrtCertType().trim();
        if (!MdtrtCertType.ELECTRONIC.getCode().equals(type)
            && !MdtrtCertType.ID_CARD.getCode().equals(type)
            && !MdtrtCertType.SOCIAL_SECURITY_CARD.getCode().equals(type)) {
            return false;
        }
        return StringUtils.isNotEmpty(context.getMdtrtCertNo());
    }
}
