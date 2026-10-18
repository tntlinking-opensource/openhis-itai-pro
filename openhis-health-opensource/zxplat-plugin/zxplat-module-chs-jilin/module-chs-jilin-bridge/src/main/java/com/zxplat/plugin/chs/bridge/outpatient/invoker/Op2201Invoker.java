/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.outpatient.invoker;

import com.zxplat.plugin.chs.domain.outpatient.command.OpPreSettleCommand;
import com.zxplat.plugin.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.zxplat.plugin.chs.domain.outpatient.entity.YbPersonCache;
import com.zxplat.plugin.chs.bridge.outpatient.support.ChsOutpatientPayerPatientMatchGate;
import com.zxplat.plugin.chs.domain.outpatient.service.YbEncounterPlatformMapDomainService;
import com.zxplat.plugin.chs.domain.outpatient.service.YbEncounterPlatformMapQueryService;
import com.zxplat.plugin.chs.domain.outpatient.service.YbPersonCacheDomainService;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.domain.share.enums.MdtrtCertType;
import com.zxplat.plugin.chs.gateway.scheme.outpatient.model.Op2201Input;
import com.zxplat.plugin.chs.gateway.scheme.outpatient.assembler.Op2201InputAssembler;
import com.zxplat.plugin.chs.gateway.scheme.outpatient.model.Op2201Output;
import com.zxplat.plugin.chs.gateway.scheme.outpatient.parser.Op2201OutputParser;
import com.zxplat.plugin.chs.gateway.scheme.outpatient.assembler.OpOutpatientSettleInputAssembler;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
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
    private final ChsOutpatientPayerPatientMatchGate payerPatientMatchGate;

    /**
     * 查询本地就诊平台映射，不存在时调用 2201 登记并落库。
     *
     * <p>map 命中时：若上下文缺少 {@code psnNo}，用 map 行回填，供后续 3101 {@code patn_id}。
     * 本次读卡 {@code psnNo} 与 map 不一致时软删错 map 后重建（防串账残留）；
     * 新建前执行 {@link ChsOutpatientPayerPatientMatchGate}。</p>
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
            boolean requestPsnPresent = StringUtils.isNotEmpty(context.getPsnNo());
            boolean mapPsnMismatchRequest = requestPsnPresent
                && StringUtils.isNotEmpty(mapped.getPsnNo())
                && !mapped.getPsnNo().trim().equals(context.getPsnNo().trim());
            // Gate：收费读卡 psn 与历史 map 不一致 → 软删错 map 后重建 2201（串账残留自愈）
            if (mapPsnMismatchRequest) {
                encounterPlatformMapDomainService.softDeleteAfterPlatformRevocation(
                    context.getEncounterId(), context.getContractId(), medType, tenantId);
            } else {
                // Gate：无本次读卡时仍核验 map 是否挂错人；有读卡且一致时完整比对患者
                this.payerPatientMatchGate.assertMappedPsnMatchesContext(context, mapped.getPsnNo());
                // Pre：签发门禁常不传 psnNo，map 命中须回填，否则 3101 patn_id 为空
                if (StringUtils.isEmpty(context.getPsnNo()) && StringUtils.isNotEmpty(mapped.getPsnNo())) {
                    context.setPsnNo(mapped.getPsnNo().trim());
                }
                return mapped.getMdtrtId();
            }
        }
        // Gate：新建 2201 前再次核验读卡人=就诊患者（enrich 已拦一层，此处防旁路）
        this.payerPatientMatchGate.assertPayerMatchesEncounterPatient(context);
        // Pre：读卡得到哪种凭证用哪种（01/02/03），禁止强制改写
        this.resolveMdtrtCert(context, tenantId);
        this.fillInsuplcFromPersonCache(context, tenantId);
        if (Boolean.TRUE.equals(context.getIdCardAsMdtrtCert()) && StringUtils.isEmpty(context.getInsuplcAdmdvs())) {
            throw new BusinessException(
                ChsErrorCodes.PLATFORM_FAILED,
                "无法取得原挂号参保地，请让患者重新读卡后再修改科室");
        }
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
     * 参保地为空时，用当日人员缓存里的参保地补上，避免报文头回落医院就医地。
     *
     * @param context  门诊预结算上下文
     * @param tenantId 租户 ID
     */
    private void fillInsuplcFromPersonCache(OpPreSettleCommand context, Long tenantId) {
        if (StringUtils.isNotEmpty(context.getInsuplcAdmdvs())) {
            return;
        }
        if (context.getContractId() == null || StringUtils.isEmpty(context.getPatientCertNo())) {
            return;
        }
        Optional<YbPersonCache> cache = personCacheDomainService.findValidByContractIdAndIdCard(
            context.getContractId(), context.getPatientCertNo().trim().toUpperCase(), tenantId);
        if (cache.isEmpty()) {
            return;
        }
        YbPersonCache row = cache.get();
        if (StringUtils.isNotEmpty(row.getInsuplcAdmdvs())) {
            context.setInsuplcAdmdvs(row.getInsuplcAdmdvs().trim());
        }
        if (StringUtils.isEmpty(context.getPsnNo()) && StringUtils.isNotEmpty(row.getPsnNo())) {
            context.setPsnNo(row.getPsnNo().trim());
        }
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
