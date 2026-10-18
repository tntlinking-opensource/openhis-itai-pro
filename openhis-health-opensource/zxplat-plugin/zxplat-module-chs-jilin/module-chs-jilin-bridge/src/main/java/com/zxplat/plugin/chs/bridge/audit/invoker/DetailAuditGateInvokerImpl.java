/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.audit.invoker;

import com.zxplat.plugin.chs.bridge.inpatient.assembler.InpatientSettleCommandAssembler;
import com.zxplat.plugin.chs.bridge.inpatient.invoker.Ip2401Invoker;
import com.zxplat.plugin.chs.bridge.outpatient.assembler.OutpatientInsuranceChargeGrouper;
import com.zxplat.plugin.chs.bridge.outpatient.assembler.OutpatientSettleCommandAssembler;
import com.zxplat.plugin.chs.bridge.outpatient.context.ChsOutpatientInsuranceContextLoader;
import com.zxplat.plugin.chs.bridge.outpatient.invoker.Op2201Invoker;
import com.zxplat.plugin.chs.domain.audit.access.Audit3101Invoker;
import com.zxplat.plugin.chs.domain.audit.access.DetailAuditGateInvoker;
import com.zxplat.plugin.chs.domain.audit.command.AuditAnalyze3101Command;
import com.zxplat.plugin.chs.domain.audit.command.DetailAuditIpGateCommand;
import com.zxplat.plugin.chs.domain.audit.command.DetailAuditOpGateCommand;
import com.zxplat.plugin.chs.domain.audit.config.ChsAuditProperties;
import com.zxplat.plugin.chs.domain.audit.result.AuditAnalyzeResult;
import com.zxplat.plugin.chs.domain.audit.result.DetailAuditGateResult;
import com.zxplat.plugin.chs.domain.audit.snapshot.AuditFeeOrderLine;
import com.zxplat.plugin.chs.domain.audit.snapshot.AuditFeeSnapshot;
import com.zxplat.plugin.chs.domain.audit.snapshot.AuditFeeSnapshotProjector;
import com.zxplat.plugin.chs.domain.audit.support.AuditViolationItemNameSupport;
import com.zxplat.plugin.chs.domain.catalog.access.ChsContractPropertiesAccess;
import com.zxplat.plugin.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.zxplat.plugin.chs.domain.contract.ChsContractSnapshot;
import com.zxplat.plugin.chs.domain.inpatient.command.IpPreSettleCommand;
import com.zxplat.plugin.chs.domain.inpatient.command.IpSettleContext;
import com.zxplat.plugin.chs.domain.inpatient.loader.IpSettleContextLoader;
import com.zxplat.plugin.chs.domain.outpatient.command.OpPreSettleCommand;
import com.zxplat.plugin.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.zxplat.plugin.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.zxplat.plugin.chs.domain.outpatient.entity.YbPersonCache;
import com.zxplat.plugin.chs.domain.outpatient.service.YbClinicPreSettleQueryService;
import com.zxplat.plugin.chs.domain.outpatient.service.YbPersonCacheDomainService;
import com.zxplat.plugin.chs.domain.share.constants.ChsDetailAuditConstants;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.plugin.chs.domain.share.enums.MedType;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 明细审核主路径门禁：装载 Snapshot、确保 mdtrt、调用 3101。
 *
 * <p>医生站签发（trig=2）常不传读卡字段：在投影前从本就诊预结算 / 人信息缓存补齐真实参保地与人员编号；
 * <b>禁止</b>用契约 {@code adm_vs}（就医地）冒充 {@code poolarea}/{@code insuplc_admdvs}。</p>
 *
 * <p>门诊 mdtrt：签发/挂号类（trig=1/2）固定复用挂号普门 map(11)；
 * 预结算/结算（trig=6/7）按本次勾选明细的 med_type（同 med_type 不同 diseCodg 仍算一次就诊），
 * 缺 map 时 {@code resolveOrRegister} 补 2201（慢病等）。</p>
 *
 * <p>自费明细（挂个人现金等非医保账户，含转自费/代煎）与预结算 {@code OutpatientInsuranceChargeGrouper}
 * 同口径剔除，不进 3101；待审集合剔空后 {@code skipped=true}，不出站。</p>
 */
@Component
@RequiredArgsConstructor
public class DetailAuditGateInvokerImpl implements DetailAuditGateInvoker {

    private static final Logger log = LoggerFactory.getLogger(DetailAuditGateInvokerImpl.class);

    private final ChsOutpatientInsuranceContextLoader opPreSettleContextLoader;
    private final IpSettleContextLoader ipSettleContextLoader;
    private final Op2201Invoker op2201Invoker;
    private final Ip2401Invoker ip2401Invoker;
    private final Audit3101Invoker audit3101Invoker;
    private final ChsInsuranceContractReader contractReader;
    private final ChsContractPropertiesAccess contractPropertiesAccess;
    private final ChsAuditProperties auditProperties;
    private final YbClinicPreSettleQueryService clinicPreSettleQueryService;
    private final YbPersonCacheDomainService personCacheDomainService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final EncounterQueryService encounterQueryService;
    /** 与门诊混合预结算同口径：剔除自费账户/代煎后再审。 */
    private final OutpatientInsuranceChargeGrouper outpatientInsuranceChargeGrouper;

    /**
     * 门诊门禁：剔自费明细 → enrich → 补齐参保地 → 解析 mdtrt → 3101。
     *
     * <p>全自费时返回 {@code skipped=true}，不出站。</p>
     *
     * @param command 门诊门禁命令
     * @return 门禁结果（含全自费 skipped）
     */
    @Override
    public DetailAuditGateResult analyzeOp(DetailAuditOpGateCommand command) {
        long gateStartNanos = System.nanoTime();
        // Gate：就诊与触发场景必填
        if (command == null || command.getEncounterId() == null) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "encounterId 不能为空");
        }
        if (StringUtils.isEmpty(command.getTrigScen())) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "trigScen 不能为空");
        }
        String trigScen = command.getTrigScen().trim();
        boolean settleAudit = this.isSettleAuditTrig(trigScen);
        // Pre：解析契约 → 装载门诊预结算上下文
        String busNo = resolveBusNo(command.getContractBusNo(), command.getContractId());
        // Pre：候选收费项（签发未传 ids 则装 PLANNED）→ 剔自费后再审
        List<Long> candidateIds = this.resolveOpCandidateChargeItemIds(command);
        List<Long> insuranceIds =
            this.filterInsuranceChargeItemIdsForAudit(command.getEncounterId(), candidateIds);
        // Gate：待审均为自费 → 与 2204 不上传同口径，跳过 3101（前端视为无违规）
        if (insuranceIds.isEmpty()) {
            return this.buildSkippedAllSelfPayResult(trigScen, busNo);
        }
        OpPreSettleCommand partial = new OpPreSettleCommand();
        partial.setContractBusNo(busNo);
        partial.setContractId(command.getContractId());
        partial.setEncounterId(command.getEncounterId());
        partial.setChargeItemIds(insuranceIds);
        partial.setPsnNo(command.getPsnNo());
        partial.setInsuplcAdmdvs(command.getInsuplcAdmdvs());
        partial.setInsutype(command.getInsutype());
        partial.setMdtrtCertType(command.getMdtrtCertType());
        partial.setMdtrtCertNo(command.getMdtrtCertNo());
        // Pre：签发固定普门 11；结算按医保明细 med_type（慢病用慢病）；收费项只装载一次供 enrich 复用
        List<ChargeItem> settleChargeItems = null;
        if (settleAudit) {
            settleChargeItems = this.loadChargeItemsForSettleAudit(insuranceIds);
            partial.setMedType(this.resolveSettleAuditMedTypeFromItems(settleChargeItems));
        } else {
            partial.setMedType(MedType.GENERAL_OUTPATIENT.getCode());
        }
        long enrichStartNanos = System.nanoTime();
        OpPreSettleCommand enriched;
        // Pre：结算路径复用已装载收费项，避免 enrich 再扫一遍
        if (settleChargeItems != null) {
            enriched = opPreSettleContextLoader.enrichPreSettle(partial, settleChargeItems);
        } else {
            enriched = opPreSettleContextLoader.enrichPreSettle(partial);
        }
        long enrichMs = (System.nanoTime() - enrichStartNanos) / 1_000_000L;
        Long tenantId = OutpatientSettleCommandAssembler.resolveTenantId(enriched.getTenantId());
        // Pre：签发常缺参保地/人员编号 — 投影前从预结算/人缓存补齐（禁止 adm_vs）
        this.backfillOpInsuredFields(enriched, tenantId);
        long mdtrtStartNanos = System.nanoTime();
        String mdtrtId;
        if (settleAudit) {
            // Pre：结算审核按当前 med_type 找/补 map（可读卡凭证；无则人缓存主凭证）
            InvokeContext invokeContext = OutpatientSettleCommandAssembler.buildInvokeContext(enriched);
            mdtrtId = op2201Invoker.resolveOrRegister(busNo, enriched, invokeContext, tenantId);
        } else {
            // Pre：签发/挂号类只复用挂号普门 map(11)；缺则提示，不盲调 2201
            enriched.setMedType(MedType.GENERAL_OUTPATIENT.getCode());
            Optional<YbEncounterPlatformMap> mappedOpt = op2201Invoker.findMapped(enriched, tenantId);
            if (mappedOpt.isEmpty()) {
                throw new BusinessException(
                    ChsErrorCodes.AUDIT_PARAM_INVALID,
                    "未找到医保挂号就诊号：请先完成医保挂号（读卡建档）后再签发/审核");
            }
            YbEncounterPlatformMap mapped = mappedOpt.get();
            if (StringUtils.isEmpty(enriched.getPsnNo()) && StringUtils.isNotEmpty(mapped.getPsnNo())) {
                enriched.setPsnNo(mapped.getPsnNo().trim());
            }
            mdtrtId = mapped.getMdtrtId();
        }
        long mdtrtMs = (System.nanoTime() - mdtrtStartNanos) / 1_000_000L;
        // Pre：map 无 psn 时再从预结算补一次
        this.backfillOpInsuredFields(enriched, tenantId);
        AuditFeeSnapshot snap = AuditFeeSnapshotProjector.fromOpPreSettle(enriched);
        fillInstitution(snap, busNo, enriched.getInsuplcAdmdvs(), command.getMedinsType(), command.getMedinsLv());
        snap.setMdtrtId(mdtrtId);
        // Pre：入参 brdy 优先，否则用 ContextLoader 写入的 Encounter 出生日期
        if (StringUtils.isNotEmpty(command.getBrdy())) {
            snap.setBrdy(command.getBrdy().trim());
        }
        this.assertOpPatientRequired(snap);
        // Cmd/Asm：出站 3101 并封装门禁结果
        long platformStartNanos = System.nanoTime();
        DetailAuditGateResult result =
            invoke3101(snap, trigScen, command.getSyscode(), busNo, command.getContractId(), mdtrtId);
        long platformMs = (System.nanoTime() - platformStartNanos) / 1_000_000L;
        long totalMs = (System.nanoTime() - gateStartNanos) / 1_000_000L;
        log.info(
            "detail-audit op gate timing: encounterId={} trigScen={} enrichMs={} mdtrtMs={} platform3101Ms={} totalMs={}",
            command.getEncounterId(),
            trigScen,
            enrichMs,
            mdtrtMs,
            platformMs,
            totalMs);
        return result;
    }

    /**
     * 住院门禁：剔自费明细 → enrich → 2401 解析/注册 → 3101。
     *
     * <p>勾选全自费时返回 {@code skipped=true}，不出站。</p>
     *
     * @param command 住院门禁命令
     * @return 门禁结果（含全自费 skipped）
     */
    @Override
    public DetailAuditGateResult analyzeIp(DetailAuditIpGateCommand command) {
        long gateStartNanos = System.nanoTime();
        // Gate：就诊必填
        if (command == null || command.getEncounterId() == null) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "encounterId 不能为空");
        }
        // Pre：trig 默认住院预结算 9
        String trigScen = command.getTrigScen();
        if (StringUtils.isEmpty(trigScen)) {
            trigScen = ChsDetailAuditConstants.TrigScen.IP_PRE_SETTLE;
        }
        // Pre：契约 → 剔自费明细 → 住院 enrich → 2401 mdtrt → Snapshot
        String busNo = resolveBusNo(command.getContractBusNo(), command.getContractId());
        List<Long> candidateIds = command.getChargeItemIds();
        // Gate：住院预结算须带收费项；空列表由 ContextLoader 报错
        if (candidateIds != null && !candidateIds.isEmpty()) {
            List<Long> insuranceIds =
                this.filterInsuranceChargeItemIdsForAudit(command.getEncounterId(), candidateIds);
            // Gate：勾选均为自费 → 跳过 3101，避免自费药仍被事前提醒
            if (insuranceIds.isEmpty()) {
                return this.buildSkippedAllSelfPayResult(trigScen, busNo);
            }
            candidateIds = insuranceIds;
        }
        IpPreSettleCommand partial = new IpPreSettleCommand();
        partial.setEncounterId(command.getEncounterId());
        partial.setChargeItemIds(candidateIds);
        partial.setContractId(command.getContractId());
        partial.setSettleMode(command.getSettleMode());
        partial.setPsnNo(command.getPsnNo());
        partial.setInsuplcAdmdvs(command.getInsuplcAdmdvs());
        partial.setInsutype(command.getInsutype());
        partial.setMdtrtCertType(command.getMdtrtCertType());
        partial.setMdtrtCertNo(command.getMdtrtCertNo());
        long enrichStartNanos = System.nanoTime();
        IpSettleContext enriched = ipSettleContextLoader.enrichPreSettle(partial);
        long enrichMs = (System.nanoTime() - enrichStartNanos) / 1_000_000L;
        // enrich 未带回 busNo 时用门禁解析结果回填，供 2401/签到
        if (StringUtils.isEmpty(enriched.getContractBusNo())) {
            enriched.setContractBusNo(busNo);
        }
        Long tenantId = InpatientSettleCommandAssembler.resolveTenantId(enriched.getTenantId());
        long mdtrtStartNanos = System.nanoTime();
        InvokeContext invokeContext = InpatientSettleCommandAssembler.buildInvokeContext(enriched);
        String mdtrtId = ip2401Invoker.resolveOrRegister(busNo, enriched, invokeContext, tenantId);
        long mdtrtMs = (System.nanoTime() - mdtrtStartNanos) / 1_000_000L;
        AuditFeeSnapshot snap = AuditFeeSnapshotProjector.fromIpSettle(enriched);
        fillInstitution(snap, busNo, enriched.getInsuplcAdmdvs(), command.getMedinsType(), command.getMedinsLv());
        snap.setMdtrtId(mdtrtId);
        // Gate：住院 3101 患者/就诊必填与门诊同检，缺 brdy 等本地拦截，避免平台「出生日期不能为空」
        this.assertOpPatientRequired(snap);
        // Cmd/Asm：出站 3101
        long platformStartNanos = System.nanoTime();
        DetailAuditGateResult result =
            invoke3101(snap, trigScen, command.getSyscode(), busNo, command.getContractId(), mdtrtId);
        long platformMs = (System.nanoTime() - platformStartNanos) / 1_000_000L;
        long totalMs = (System.nanoTime() - gateStartNanos) / 1_000_000L;
        log.info(
            "detail-audit ip gate timing: encounterId={} trigScen={} enrichMs={} mdtrtMs={} platform3101Ms={} totalMs={}",
            command.getEncounterId(),
            trigScen,
            enrichMs,
            mdtrtMs,
            platformMs,
            totalMs);
        return result;
    }

    /**
     * 将 Snapshot 转为 3101 Command 并出站；组装门禁结果（含违规列表）。
     *
     * @param snap       已补齐机构与 mdtrt 的费用快照
     * @param trigScen   触发场景码
     * @param syscode    系统代码（可空，由 Invoker 补配置）
     * @param busNo      契约 bus_no
     * @param contractId 契约主键
     * @param mdtrtId    医保就诊 ID
     * @return 门禁结果（skipped=false）
     */
    private DetailAuditGateResult invoke3101(
            AuditFeeSnapshot snap,
            String trigScen,
            String syscode,
            String busNo,
            Long contractId,
            String mdtrtId) {
        // Asm：Snapshot → 3101 Command
        AuditAnalyze3101Command analyze = AuditFeeSnapshotProjector.toAnalyzeCommand(snap, trigScen, syscode);
        analyze.setContractBusNo(busNo);
        analyze.setContractId(contractId);
        // Cmd：出站 3101（落库在 Audit3101Invoker）
        AuditAnalyzeResult result = audit3101Invoker.invoke(analyze);
        // Pre：平台明细无品名，按 Snapshot 医嘱行回填药品/项目名称供弹窗展示
        AuditViolationItemNameSupport.enrichItemNames(result, snap);
        return new DetailAuditGateResult()
            .setSkipped(false)
            .setMdtrtId(mdtrtId)
            .setTrigScen(trigScen)
            .setContractBusNo(busNo)
            .setAnalyzeResult(result);
    }

    /**
     * 补齐机构编码/区划/类型/等级：优先契约配置与入参，缺省用 audit 默认项。
     *
     * <p>此处 {@code fallbackAdmdvs} 仅回填 {@code medins_admdvs}（就医地缺省），
     * <b>不得</b>当作 {@code poolarea} 来源。</p>
     *
     * @param snap           待写入 Snapshot
     * @param busNo          契约 bus_no（查 fixmedins / admdvs）
     * @param fallbackAdmdvs 参保地（仅当契约未配就医地时回填 medins_admdvs）
     * @param medinsType     入参机构类型（可空）
     * @param medinsLv       入参机构等级（可空）
     */
    private void fillInstitution(
            AuditFeeSnapshot snap,
            String busNo,
            String fallbackAdmdvs,
            String medinsType,
            String medinsLv) {
        // Pre：定点机构编码、名称与医保区划
        contractPropertiesAccess.findFixmedinsCode(busNo).ifPresent(snap::setMedinsId);
        contractPropertiesAccess.findFixmedinsName(busNo).ifPresent(snap::setMedinsName);
        contractPropertiesAccess.findAdmVs(busNo).ifPresent(snap::setMedinsAdmdvs);
        // 契约未配区划时回退参保地，避免 3101 medins_admdvs 空（≠ 用就医地填 poolarea）
        if (StringUtils.isEmpty(snap.getMedinsAdmdvs())) {
            snap.setMedinsAdmdvs(fallbackAdmdvs);
        }
        // 类型/等级：入参优先，否则配置默认（试调页可显式传入）
        if (StringUtils.isNotEmpty(medinsType)) {
            snap.setMedinsType(medinsType);
        } else {
            snap.setMedinsType(auditProperties.getDefaultMedinsType());
        }
        if (StringUtils.isNotEmpty(medinsLv)) {
            snap.setMedinsLv(medinsLv);
        } else {
            snap.setMedinsLv(auditProperties.getDefaultMedinsLv());
        }
    }

    /**
     * 解析契约 bus_no（busNo 与 contractId 二选一，均空则抛业务异常）。
     *
     * @param contractBusNo 契约编号
     * @param contractId    契约主键
     * @return 非空 bus_no
     */
    private String resolveBusNo(String contractBusNo, Long contractId) {
        ChsContractSnapshot contract = contractReader.requireContract(contractBusNo, contractId);
        return contractReader.requireContractBusNo(contract);
    }

    /**
     * 是否结算类审核触发场景（预结算 7 / 结算 6）：按当前结算 med_type 取 mdtrt。
     *
     * @param trigScen 触发场景
     * @return 结算类为 true
     */
    private boolean isSettleAuditTrig(String trigScen) {
        return ChsDetailAuditConstants.TrigScen.OP_PRE_SETTLE.equals(trigScen)
            || ChsDetailAuditConstants.TrigScen.OP_SETTLE.equals(trigScen);
    }

    /**
     * 解析门诊门禁候选收费项 ID：入参有 ids 用入参；签发未传则按就诊装载 PLANNED。
     *
     * @param command 门诊门禁命令
     * @return 非空候选 ID 列表
     * @throws BusinessException 无待收费明细时
     */
    private List<Long> resolveOpCandidateChargeItemIds(DetailAuditOpGateCommand command) {
        List<Long> ids = command.getChargeItemIds();
        // Pre：收费预结算等已传勾选 ID，直接使用
        if (ids != null && !ids.isEmpty()) {
            return ids;
        }
        // Pre：签发门禁未传 ids → 与 ContextLoader 同口径装 PLANNED
        ChargeItemQueryCommand byEncounter = new ChargeItemQueryCommand();
        byEncounter.setEncounterId(command.getEncounterId());
        byEncounter.setStatusEnumIn(List.of(ChargeItemStatus.PLANNED.getCode()));
        List<ChargeItem> plannedItems = chargeItemQueryService.listChargeItemsByEncounterId(byEncounter);
        // Gate：无待收费行则无法审，与 enrich 报错文案一致
        if (plannedItems == null || plannedItems.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND, "就诊下无待收费明细，无法做事前审核");
        }
        List<Long> plannedIds = new ArrayList<>(plannedItems.size());
        for (ChargeItem item : plannedItems) {
            // 跳过空行，避免 NPE
            if (item != null && item.getId() != null) {
                plannedIds.add(item.getId());
            }
        }
        // Gate：装载后无有效主键则同上拒绝
        if (plannedIds.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND, "就诊下无待收费明细，无法做事前审核");
        }
        return plannedIds;
    }

    /**
     * 按预结算 Grouper 口径保留医保账户收费项 ID（自费/代煎剔除）。
     *
     * @param encounterId   就诊主键
     * @param candidateIds  待筛选 ID
     * @return 医保项 ID；候选空或全自费时为空列表
     */
    private List<Long> filterInsuranceChargeItemIdsForAudit(Long encounterId, List<Long> candidateIds) {
        // 无候选则无需 Grouper，由上游决定是否报错/跳过
        if (candidateIds == null || candidateIds.isEmpty()) {
            return List.of();
        }
        return outpatientInsuranceChargeGrouper.listInsuranceChargeItemIds(encounterId, candidateIds);
    }

    /**
     * 组装「全自费跳过 3101」门禁结果（未出站；前端不弹违规窗）。
     *
     * @param trigScen 触发场景回显
     * @param busNo    契约 bus_no 回显
     * @return skipped=true 的门禁结果
     */
    private DetailAuditGateResult buildSkippedAllSelfPayResult(String trigScen, String busNo) {
        return new DetailAuditGateResult()
            .setSkipped(true)
            .setSkipReason(ChsDetailAuditConstants.SKIP_REASON_ALL_SELF_PAY)
            .setTrigScen(trigScen)
            .setContractBusNo(busNo)
            .setAnalyzeResult(null);
    }

    /**
     * 结算审核：按 ids 装载收费项（空列表返回空，不抛错）。
     *
     * @param chargeItemIds 勾选收费项
     * @return 收费项列表；ids 空时 empty
     */
    private List<ChargeItem> loadChargeItemsForSettleAudit(List<Long> chargeItemIds) {
        if (chargeItemIds == null || chargeItemIds.isEmpty()) {
            return List.of();
        }
        ChargeItemQueryCommand query = new ChargeItemQueryCommand();
        query.setIds(chargeItemIds);
        return chargeItemQueryService.loadChargeItemsByIds(query);
    }

    /**
     * 结算审核：从已装载收费项绑定诊断解析唯一 med_type（跨类型拒绝）。
     *
     * <p>同 med_type、不同 diseCodg 仍算一次就诊，不拆拒绝。
     * 未绑定诊断的行不参与计数，与收费预结算 BR-OPC-002 继承口径一致。</p>
     *
     * @param chargeItems 已装载收费项
     * @return 平台 med_type
     */
    private String resolveSettleAuditMedTypeFromItems(List<ChargeItem> chargeItems) {
        if (chargeItems == null || chargeItems.isEmpty()) {
            return MedType.GENERAL_OUTPATIENT.getCode();
        }
        LinkedHashSet<Long> dxIds = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            this.collectEncounterDiagnosisIdsFromIds(item.getEncounterDiagnosisIds(), dxIds);
        }
        Map<Long, String> medTypeByDxId = encounterQueryService.mapMedTypeByEncounterDiagnosisIds(dxIds);
        LinkedHashSet<String> medTypes = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            Long primaryDxId = this.firstEncounterDiagnosisIdFromIds(item.getEncounterDiagnosisIds());
            // 无诊断绑定：不单独计为普通门诊（并入同批已绑诊断的唯一 med_type）
            if (primaryDxId == null) {
                continue;
            }
            String mapped = medTypeByDxId.get(primaryDxId);
            if (StringUtils.isEmpty(mapped)) {
                continue;
            }
            medTypes.add(mapped.trim());
            if (medTypes.size() > 1) {
                throw new BusinessException(
                    ChsErrorCodes.AUDIT_PARAM_INVALID,
                    "普通门诊与慢病等须分次结算（起付线），请每次只勾选同一就诊类型的明细");
            }
        }
        if (medTypes.isEmpty()) {
            return MedType.GENERAL_OUTPATIENT.getCode();
        }
        return medTypes.iterator().next();
    }

    /**
     * 从 encounter_diagnosis_ids 文本收集诊断主键。
     *
     * @param idsText 逗号分隔 ids
     * @param target  目标集合
     */
    private void collectEncounterDiagnosisIdsFromIds(String idsText, Set<Long> target) {
        if (StringUtils.isEmpty(idsText) || target == null) {
            return;
        }
        for (String part : idsText.split(",")) {
            if (StringUtils.isEmpty(part)) {
                continue;
            }
            String trimmed = part.trim();
            if (StringUtils.isEmpty(trimmed)) {
                continue;
            }
            try {
                target.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // 跳过非法段
            }
        }
    }

    /**
     * 取 encounter_diagnosis_ids 文本中第一个合法诊断主键。
     *
     * @param idsText 逗号分隔 ids
     * @return 首个合法 Long；无则 null
     */
    private Long firstEncounterDiagnosisIdFromIds(String idsText) {
        if (StringUtils.isEmpty(idsText)) {
            return null;
        }
        for (String part : idsText.split(",")) {
            if (StringUtils.isEmpty(part)) {
                continue;
            }
            String trimmed = part.trim();
            if (StringUtils.isEmpty(trimmed)) {
                continue;
            }
            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException ignored) {
                // 跳过非法段
            }
        }
        return null;
    }

    /**
     * 从本就诊预结算 / 人信息缓存补齐门诊参保字段。
     *
     * <p>签发（trig=2）常不传读卡字段：须补齐 {@code psnNo}/{@code insuplcAdmdvs}/{@code insutype}。
     * 就诊凭证由 {@code Op2201Invoker} 按读卡原样解析（上下文或人缓存主凭证）；此处不覆盖
     * {@code mdtrtCert*}。禁止契约 {@code adm_vs} 兜底参保地。</p>
     *
     * @param ctx      已 enrich 的门诊上下文
     * @param tenantId 租户
     */
    private void backfillOpInsuredFields(OpPreSettleCommand ctx, Long tenantId) {
        boolean needPsn = StringUtils.isEmpty(ctx.getPsnNo());
        boolean needInsuplc = !this.isValidInsuplcAdmdvs(ctx.getInsuplcAdmdvs());
        boolean needInsutype = StringUtils.isEmpty(ctx.getInsutype());
        if (!needPsn && !needInsuplc && !needInsutype) {
            return;
        }
        // Pre：本就诊预结算（含 SETTLED 挂号 2206），同契约优先
        List<YbClinicPreSettle> preRows = clinicPreSettleQueryService.listByEncounterIds(
            List.of(ctx.getEncounterId()), tenantId);
        YbClinicPreSettle matchedPre = this.pickPreSettleForBackfill(preRows, ctx.getContractId());
        if (matchedPre != null) {
            if (needPsn && StringUtils.isNotEmpty(matchedPre.getPsnNo())) {
                ctx.setPsnNo(matchedPre.getPsnNo().trim());
                needPsn = false;
            }
            if (needInsuplc && this.isValidInsuplcAdmdvs(matchedPre.getInsuplcAdmdvs())) {
                ctx.setInsuplcAdmdvs(matchedPre.getInsuplcAdmdvs().trim());
                needInsuplc = false;
            }
            if (needInsutype && StringUtils.isNotEmpty(matchedPre.getInsutype())) {
                ctx.setInsutype(matchedPre.getInsutype().trim());
                needInsutype = false;
            }
        }
        if (!needPsn && !needInsuplc && !needInsutype) {
            return;
        }
        // Pre：1101 人信息缓存（证件号来自 Encounter）— 只补人员/参保地，不回填读卡令牌
        if (ctx.getContractId() != null && StringUtils.isNotEmpty(ctx.getPatientCertNo())) {
            Optional<YbPersonCache> cacheOpt = personCacheDomainService.findValidByContractIdAndIdCard(
                ctx.getContractId(), ctx.getPatientCertNo().trim(), tenantId);
            if (cacheOpt.isPresent()) {
                YbPersonCache cache = cacheOpt.get();
                if (needPsn && StringUtils.isNotEmpty(cache.getPsnNo())) {
                    ctx.setPsnNo(cache.getPsnNo().trim());
                    needPsn = false;
                }
                if (needInsuplc && this.isValidInsuplcAdmdvs(cache.getInsuplcAdmdvs())) {
                    ctx.setInsuplcAdmdvs(cache.getInsuplcAdmdvs().trim());
                    needInsuplc = false;
                }
                if (needInsutype && StringUtils.isNotEmpty(cache.getInsutype())) {
                    ctx.setInsutype(cache.getInsutype().trim());
                }
            }
        }
        // Gate：参保地仍缺则本地失败，禁止 adm_vs 冒充
        if (needInsuplc) {
            throw new BusinessException(
                ChsErrorCodes.AUDIT_PARAM_INVALID,
                "参保所属统筹区(insuplc_admdvs)不能为空：请重新读卡或完成收费预结算后再签发");
        }
    }

    /**
     * 选取用于回填的预结算行：同契约优先，否则取列表首条（创建时间降序）。
     *
     * @param preRows    本就诊预结算列表
     * @param contractId 当前契约
     * @return 命中行；无则 null
     */
    private YbClinicPreSettle pickPreSettleForBackfill(List<YbClinicPreSettle> preRows, Long contractId) {
        if (preRows == null || preRows.isEmpty()) {
            return null;
        }
        if (contractId != null) {
            for (YbClinicPreSettle row : preRows) {
                if (row != null && contractId.equals(row.getContractId())) {
                    return row;
                }
            }
        }
        return preRows.get(0);
    }

    /**
     * 校验参保所属统筹区：长度 1～10，且不得以机构码前缀 H 冒充。
     *
     * @param value 候选值
     * @return true 表示可作为 poolarea / insuplc_admdvs
     */
    private boolean isValidInsuplcAdmdvs(String value) {
        if (StringUtils.isEmpty(value)) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.length() < 1 || trimmed.length() > 10) {
            return false;
        }
        // 拒绝定点机构码（如 H22010402403）
        if (trimmed.regionMatches(true, 0, "H", 0, 1)) {
            return false;
        }
        return true;
    }

    /**
     * 3101 出站前按 apis/3101 表 115/116/118 必填项做本地总检，一次抛出全部缺口。
     *
     * @param snap 已投影并补齐机构的费用快照
     */
    private void assertOpPatientRequired(AuditFeeSnapshot snap) {
        ArrayList<String> gaps = new ArrayList<>();
        if (StringUtils.isEmpty(snap.getPsnNo())) {
            gaps.add("patn_id/psn_no");
        }
        if (StringUtils.isEmpty(snap.getGend())
            || snap.getGend().trim().length() < 1
            || snap.getGend().trim().length() > 3) {
            gaps.add("gend(须1～3位医保码)");
        }
        if (StringUtils.isEmpty(snap.getBrdy())) {
            gaps.add("brdy");
        }
        if (!this.isValidInsuplcAdmdvs(snap.getPoolarea())) {
            gaps.add("poolarea/insuplc_admdvs");
        }
        if (StringUtils.isEmpty(snap.getMedinsId())) {
            gaps.add("medins_id");
        }
        if (StringUtils.isEmpty(snap.getMedinsName())
            || snap.getMedinsName().trim().length() > ChsDetailAuditConstants.MEDINS_NAME_MAX_LEN) {
            gaps.add("medins_name(契约fixmedins_name,1～200)");
        }
        if (StringUtils.isEmpty(snap.getMedinsAdmdvs())) {
            gaps.add("medins_admdvs");
        }
        if (StringUtils.isEmpty(snap.getAdmDate())) {
            gaps.add("adm_date");
        }
        if (StringUtils.isEmpty(snap.getDscgDate())) {
            gaps.add("dscg_date");
        }
        if (StringUtils.isEmpty(snap.getDscgMainDiseCodg())) {
            gaps.add("dscg_main_dise_codg");
        }
        if (StringUtils.isEmpty(snap.getDrCodg())) {
            gaps.add("dr_codg(医师yb_no)");
        }
        if (!this.isValidDeptCodg(snap.getAdmDeptCodg())) {
            gaps.add("adm_dept_codg(科室yb_no/bus_no,1～30)");
        }
        if (StringUtils.isEmpty(snap.getAdmDeptName())) {
            gaps.add("adm_dept_name");
        }
        if (!this.isValidDeptCodg(snap.getDscgDeptCodg())) {
            gaps.add("dscg_dept_codg");
        }
        if (StringUtils.isEmpty(snap.getDscgDeptName())) {
            gaps.add("dscg_dept_name");
        }
        if (snap.getOrders() == null || snap.getOrders().isEmpty()) {
            gaps.add("fsi_order_dtos(至少一行)");
        } else {
            int i = 0;
            for (AuditFeeOrderLine order : snap.getOrders()) {
                i++;
                if (order == null) {
                    gaps.add("order[" + i + "]空");
                    continue;
                }
                if (StringUtils.isEmpty(order.getRxId())) {
                    gaps.add("order[" + i + "].rx_id");
                }
                if (StringUtils.isEmpty(order.getHilistCode())) {
                    gaps.add("order[" + i + "].hilist_code");
                }
                if (StringUtils.isEmpty(order.getHilistName())) {
                    gaps.add("order[" + i + "].hilist_name");
                }
                if (!this.isValidDeptCodg(order.getDrordDeptCodg())) {
                    gaps.add("order[" + i + "].drord_dept_codg");
                }
                if (StringUtils.isEmpty(order.getDrordDrCodg())) {
                    gaps.add("order[" + i + "].drord_dr_codg");
                }
            }
        }
        if (!gaps.isEmpty()) {
            throw new BusinessException(
                ChsErrorCodes.AUDIT_PARAM_INVALID,
                "3101 报文必填缺失（请一次补齐）：" + String.join("；", gaps));
        }
    }

    /**
     * 科室平台码合法性：非空且长度 1～{@link ChsDetailAuditConstants#DEPT_CODG_MAX_LEN}。
     *
     * @param value 候选
     * @return true 合法
     */
    private boolean isValidDeptCodg(String value) {
        if (StringUtils.isEmpty(value)) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.length() >= 1 && trimmed.length() <= ChsDetailAuditConstants.DEPT_CODG_MAX_LEN;
    }
}
