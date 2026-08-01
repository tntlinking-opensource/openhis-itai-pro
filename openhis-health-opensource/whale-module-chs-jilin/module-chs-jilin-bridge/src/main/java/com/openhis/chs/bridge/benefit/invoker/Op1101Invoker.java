/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.benefit.invoker;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.chs.bridge.benefit.mapper.Op1101ResultMapper;
import com.openhis.chs.domain.outpatient.result.YbPersonInfoResult;
import com.openhis.chs.domain.outpatient.access.Op1101QueryInvoker;
import com.openhis.chs.domain.outpatient.command.YbPersonQueryCommand;
import com.openhis.chs.domain.outpatient.entity.YbPersonCache;
import com.openhis.chs.domain.outpatient.service.YbPersonCacheDomainService;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.domain.share.support.ChsTextSupport;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.benefit.model.Op1101Input;
import com.openhis.chs.gateway.scheme.benefit.assembler.Op1101InputAssembler;
import com.openhis.chs.gateway.scheme.benefit.model.Op1101Output;
import com.openhis.chs.gateway.scheme.benefit.parser.Op1101OutputParser;
import com.openhis.chs.domain.share.constants.ChsPlatformCodes;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 【1101】人员信息获取 Invoker：读卡查询参保人基本信息、险种与账户余额。
 *
 * <p>流程：当日缓存命中直接返回（除非 forceRefresh）→ 否则 gateway 出站 → 结果写入 {@code yb_person_cache}。
 * 社保卡（凭证类型 03）解密失败时自动回落身份证（02）重试。</p>
 *
 * <p>实现 domain {@link Op1101QueryInvoker}，供挂号读卡、待遇校验等场景共用。</p>
 */
@Component
@RequiredArgsConstructor
public class Op1101Invoker implements Op1101QueryInvoker {

    private final InsuranceGatewayClient gatewayClient;
    private final YbPersonCacheDomainService personCacheDomainService;

    /**
     * 读卡获取人员信息。
     *
     * <p>默认命中当日缓存直接返回；{@link YbPersonQueryCommand#isForceRefresh()} 为 true 时跳过缓存出站，
     * 以保证个账余额等字段与平台一致。</p>
     *
     * @param command contractBusNo、就诊凭证（mdtrtCertType/No）、cardSn、证件号及 operatorId
     * @return 人员编号、险种、参保地、账户余额及参保/身份明细列表
     */
    public YbPersonInfoResult invoke(YbPersonQueryCommand command) {
        validatePersonQuery(command);
        Long tenantId = resolveTenantId(command.getTenantId());
        if (!command.isForceRefresh()) {
            Optional<YbPersonCache> cached = personCacheDomainService.findValidPersonCache(command, tenantId);
            if (cached.isPresent()) {
                return Op1101ResultMapper.fromCache(cached.get());
            }
        }

        InvokeContext invokeContext = InvokeContext.builder()
            .operatorId(String.valueOf(command.getOperatorId()))
            .insuplcAdmdvs(command.getInsuplcAdmdvs())
            .build();
        Op1101Input input = Op1101InputAssembler.assemble(
            command.getMdtrtCertType(),
            command.getMdtrtCertNo(),
            command.getCardSn(),
            command.getPsnCertType(),
            command.getCertno(),
            command.getPsnName());
        ResultBody body = invokeWithIdCardFallback(command, invokeContext, input);
        Op1101Output output = Op1101OutputParser.requireOutput(body);
        YbPersonInfoResult result = Op1101ResultMapper.fromOutput(output, command.getInsuplcAdmdvs());
        personCacheDomainService.persistFrom1101(command, result, body.getOutput(), tenantId);
        return result;
    }

    /**
     * 发起 1101 出站；社保卡解密失败时自动回落身份证重试。
     *
     * @param command 人员查询命令
     * @param invokeContext 出站调用上下文
     * @param input 1101 入参
     * @return 平台响应体
     */
    private ResultBody invokeWithIdCardFallback(
            YbPersonQueryCommand command,
            InvokeContext invokeContext,
            Op1101Input input) {
        ResultBody body = gatewayClient.invoke(
            command.getContractBusNo(), ChsInfNo.PERSON_INFO, input, invokeContext);
        if (!shouldRetryWithIdCard(body, command)) {
            return body;
        }
        String certno = command.getCertno().trim();
        Op1101Input input02 = Op1101InputAssembler.assemble(
            "02", certno, null, command.getPsnCertType(), certno, null);
        ResultBody retried = gatewayClient.invoke(
            command.getContractBusNo(), ChsInfNo.PERSON_INFO, input02, invokeContext);
        if (retried != null && ChsPlatformCodes.INFCODE_SUCCESS.equals(retried.getInfcode())) {
            command.setMdtrtCertType("02");
            command.setMdtrtCertNo(certno);
            command.setCardSn(null);
        }
        return retried;
    }

    /**
     * 判断社保卡读卡失败后是否应以身份证凭证重试。
     *
     * @param body 平台响应体
     * @param command 人员查询命令
     * @return 是否应回落身份证重试
     */
    private static boolean shouldRetryWithIdCard(ResultBody body, YbPersonQueryCommand command) {
        if (body == null || ChsPlatformCodes.INFCODE_SUCCESS.equals(body.getInfcode())) {
            return false;
        }
        if (!"03".equals(trimToEmpty(command.getMdtrtCertType()))) {
            return false;
        }
        if (isEmpty(command.getCertno())) {
            return false;
        }
        String err = ChsTextSupport.platformErrMsg(body, "");
        return err.contains("解密卡串") || err.contains("encodeCardReturnCardNo");
    }

    /**
     * 校验 1101 人员查询命令必填项。
     *
     * @param command 人员查询命令
     */
    private static void validatePersonQuery(YbPersonQueryCommand command) {
        if (command == null || command.getContractId() == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractId 不能为空");
        }
        if (isEmpty(command.getContractBusNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractBusNo 不能为空");
        }
        if (isEmpty(command.getMdtrtCertType()) || isEmpty(command.getMdtrtCertNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "就诊凭证不能为空");
        }
        if ("03".equals(command.getMdtrtCertType().trim()) && isEmpty(command.getCardSn())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "社保卡读卡缺少卡识别card_sn");
        }
        if ("01".equals(command.getMdtrtCertType().trim())) {
            if (isEmpty(command.getCertno())) {
                throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "电子凭证读码缺少证件号码 certno");
            }
            if (isEmpty(command.getPsnName())) {
                throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "电子凭证读码缺少人员姓名 psn_name");
            }
        }
    }

    /**
     * 解析租户 ID，为空时取当前会话租户。
     *
     * @param tenantId 调用方传入的租户 ID
     * @return 有效租户 ID
     */
    private static Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
