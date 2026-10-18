/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inpatient.invoker;

import com.zxplat.plugin.chs.domain.inpatient.command.IpSettleContext;
import com.zxplat.plugin.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.zxplat.plugin.chs.domain.outpatient.service.YbEncounterPlatformMapDomainService;
import com.zxplat.plugin.chs.domain.outpatient.service.YbEncounterPlatformMapQueryService;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.gateway.observability.ChsInvokeLogContextHolder;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.inpatient.model.Ip2401Input;
import com.zxplat.plugin.chs.gateway.scheme.inpatient.model.Ip2401Output;
import com.zxplat.plugin.chs.gateway.scheme.inpatient.parser.Ip2401OutputParser;
import com.zxplat.plugin.chs.gateway.scheme.inpatient.assembler.IpInpatientSettleInputAssembler;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 【2401】住院入院登记 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；副作用：读/写 yb_encounter_platform_map。</p>
 */
@Component
@RequiredArgsConstructor
public class Ip2401Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final YbEncounterPlatformMapQueryService encounterPlatformMapQueryService;
    private final YbEncounterPlatformMapDomainService encounterPlatformMapDomainService;

    /**
     * 查询本地就诊平台映射，不存在时调用 2401 入院登记并落库。
     *
     * <p>map 命中时：若上下文缺少 {@code psnNo}，用 map 行回填，供后续 3101 {@code patn_id}。</p>
     *
     * @param contractBusNo 医保契约 bus_no
     * @param context       住院结算上下文（就诊、费用性质、人员编号等）
     * @param invokeContext 操作员与签到上下文
     * @param tenantId      租户 ID
     * @return 医保平台就诊 ID（mdtrt_id）
     */
    public String resolveOrRegister(
            String contractBusNo,
            IpSettleContext context,
            InvokeContext invokeContext,
            Long tenantId) {
        String medType = IpInpatientSettleInputAssembler.resolveMedTypeForContext(context);
        Optional<YbEncounterPlatformMap> mappedOpt = encounterPlatformMapQueryService
            .findOptionalByEncounterContractMedType(
                context.getEncounterId(), context.getContractId(), medType, tenantId);
        if (mappedOpt.isPresent()) {
            YbEncounterPlatformMap mapped = mappedOpt.get();
            // Pre：门禁常不传 psnNo，map 命中须回填，否则 3101 patn_id 为空
            if (StringUtils.isEmpty(context.getPsnNo()) && StringUtils.isNotEmpty(mapped.getPsnNo())) {
                context.setPsnNo(mapped.getPsnNo().trim());
            }
            return mapped.getMdtrtId().trim();
        }
        Ip2401Input input2401 = IpInpatientSettleInputAssembler.assemble2401(context);
        // Cmd：出站 2401，并把就诊写入调用日志，列表才能按就诊号和患者检索
        ResultBody body2401 = this.invokeAdmission(
            contractBusNo, input2401, invokeContext, context, tenantId);
        Ip2401Output output2401 = Ip2401OutputParser.requireResult(body2401);
        String mdtrtId = Ip2401OutputParser.resolveMdtrtId(output2401);
        encounterPlatformMapDomainService.saveAfterPlatformRegistration(
            context.getEncounterId(),
            context.getContractId(),
            medType,
            mdtrtId,
            context.getPsnNo(),
            context.getEncounterBusNo(),
            tenantId);
        return mdtrtId;
    }

    /**
     * 强制重新办理 2401 入院登记（先软删本地 map，再出站），返回新 mdtrt_id。
     *
     * <p>用于旧就诊已离院且无法 2405、需换新就诊号结算的恢复路径。</p>
     *
     * @param contractBusNo 医保契约 bus_no
     * @param context       住院结算上下文（须含入院/诊断等 2401 字段）
     * @param invokeContext 操作员与签到上下文
     * @param tenantId      租户 ID
     * @return 新的平台就诊 ID
     */
    public String registerFresh(
            String contractBusNo,
            IpSettleContext context,
            InvokeContext invokeContext,
            Long tenantId) {
        String medType = IpInpatientSettleInputAssembler.resolveMedTypeForContext(context);
        encounterPlatformMapDomainService.softDeleteEncounterMap(
            context.getEncounterId(),
            context.getContractId(),
            null,
            medType,
            context.getOperatorId(),
            tenantId);
        Ip2401Input input2401 = IpInpatientSettleInputAssembler.assemble2401(context);
        // Cmd：重新入院同样要带上就诊，否则日志页仍对不上患者
        ResultBody body2401 = this.invokeAdmission(
            contractBusNo, input2401, invokeContext, context, tenantId);
        Ip2401Output output2401 = Ip2401OutputParser.requireResult(body2401);
        String mdtrtId = Ip2401OutputParser.resolveMdtrtId(output2401);
        encounterPlatformMapDomainService.saveAfterPlatformRegistration(
            context.getEncounterId(),
            context.getContractId(),
            medType,
            mdtrtId,
            context.getPsnNo(),
            context.getEncounterBusNo(),
            tenantId);
        return mdtrtId;
    }

    /**
     * 出站 2401，并把就诊、契约、租户写入调用日志。
     *
     * <p>住院登记入口本身不设日志上下文，出站前补上后，日志页才能用就诊号反查患者姓名。
     * 预结算等调用方已持有上下文时只合并字段，出站后不清理，避免打断外层链路。</p>
     *
     * @param contractBusNo 医保契约 bus_no
     * @param input2401     2401 报文
     * @param invokeContext 操作员与签到上下文
     * @param context       住院结算上下文（含就诊、契约）
     * @param tenantId      租户 ID
     * @return 平台响应体
     */
    private ResultBody invokeAdmission(
            String contractBusNo,
            Ip2401Input input2401,
            InvokeContext invokeContext,
            IpSettleContext context,
            Long tenantId) {
        // 登记路径通常没有外层日志上下文；预结算已设置过则不能在这里清理
        boolean createdHere = ChsInvokeLogContextHolder.get() == null;
        ChsInvokeLogContextHolder.merge(ctx -> {
            // 有就诊主键才写入，空值不覆盖预结算已挂上的就诊
            if (context.getEncounterId() != null) {
                ctx.setEncounterId(context.getEncounterId());
            }
            // 有契约才写入，供日志按契约筛选
            if (context.getContractId() != null) {
                ctx.setContractId(context.getContractId());
            }
            // 有租户才写入，避免空租户覆盖外层
            if (tenantId != null) {
                ctx.setTenantId(tenantId);
            }
        });
        try {
            return gatewayClient.invoke(
                contractBusNo, ChsInfNo.INPATIENT_ADMISSION, input2401, invokeContext);
        } finally {
            // 仅释放本次新建的上下文，防止线程复用把上一位患者的就诊号带到下一次调用
            if (createdHere) {
                ChsInvokeLogContextHolder.clear();
            }
        }
    }
}
