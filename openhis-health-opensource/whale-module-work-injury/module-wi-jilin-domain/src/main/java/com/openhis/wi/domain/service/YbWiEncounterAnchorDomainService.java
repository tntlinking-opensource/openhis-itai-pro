/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.service;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.wi.domain.share.WiErrorCodes;
import com.openhis.wi.domain.share.WorkInjuryConstants;
import com.openhis.wi.domain.settle.command.WiSettleContext;
import com.openhis.wi.domain.entity.YbWiEncounterAnchor;
import com.openhis.wi.domain.repository.YbWiEncounterAnchorRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 工伤就诊锚点 {@code yb_wi_encounter_anchor} 写侧（1101/9102/2301/2201～2208 状态机）。 */
@Service
@ConditionalOnProperty(name = "openhis.modules.work-injury.enabled", havingValue = "true")
@RequiredArgsConstructor
public class YbWiEncounterAnchorDomainService {

    private final YbWiEncounterAnchorRepository encounterAnchorRepository;

    /**
     * 按 encounter 创建或合并 anchor（9102/2201 链状态）。
     *
     * @param encounterId 就诊 ID
     * @param contractId  费用性质 contract
     * @param ctx         HIS 上下文（可空；非空时回填 psn_no/ipt_otp_no 等）
     * @return 持久化后的锚点
     */
    @Transactional
    public YbWiEncounterAnchor ensureAnchor(Long encounterId, Long contractId, WiSettleContext ctx) {
        if (encounterId == null) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "encounterId 不能为空");
        }
        if (contractId == null) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "contractId 不能为空");
        }
        YbWiEncounterAnchor anchor = encounterAnchorRepository.findByEncounterId(encounterId).orElse(null);
        if (anchor == null) {
            anchor = new YbWiEncounterAnchor();
            anchor.setEncounterId(encounterId);
            anchor.setContractId(contractId);
            anchor.setQualificationStatus(WorkInjuryConstants.QUALIFICATION_NONE);
            anchor.setHasActiveFeeUpload(Boolean.FALSE);
            anchor.setMedType(WorkInjuryConstants.DEFAULT_MED_TYPE);
            anchor.setTenantId(TenantUtils.getCurrentTenantId());
        }
        mergeFromContext(anchor, ctx);
        if (isEmpty(anchor.getQualificationStatus())) {
            anchor.setQualificationStatus(WorkInjuryConstants.QUALIFICATION_NONE);
        }
        return encounterAnchorRepository.save(anchor);
    }

    /**
     * OP_SETTLE 前校验 qualification_id 与 9102 状态（WI-004）。
     *
     * @param anchor 就诊锚点
     * @throws BusinessException {@link WiErrorCodes#NO_QUALIFICATION} / {@link WiErrorCodes#APPROVAL_NOT_FOLLOWED_BY_9102}
     */
    public void assertQualificationForSettle(YbWiEncounterAnchor anchor) {
        if (anchor == null || isEmpty(anchor.getQualificationId())) {
            throw new BusinessException(WiErrorCodes.NO_QUALIFICATION, "缺少 qualification_id，请先完成 9102 审批查询");
        }
        if (WorkInjuryConstants.QUALIFICATION_PENDING_9102.equals(anchor.getQualificationStatus())) {
            throw new BusinessException(
                    WiErrorCodes.APPROVAL_NOT_FOLLOWED_BY_9102,
                    "2301 后须再完成 9102 确认审批（WI-004）");
        }
        if (!WorkInjuryConstants.QUALIFICATION_VALID.equals(anchor.getQualificationStatus())) {
            throw new BusinessException(WiErrorCodes.NO_QUALIFICATION, "审批资格未生效，请先完成 9102 并绑定 qualification_id");
        }
    }

    /**
     * 【1101】submit 后写入 psn_no / injure_affirm_id。
     *
     * @param anchor         就诊锚点
     * @param psnNo          平台人员编号
     * @param injureAffirmId 认定 ID（可空）
     */
    @Transactional
    public void apply1101Submit(YbWiEncounterAnchor anchor, String psnNo, String injureAffirmId) {
        if (anchor == null) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "就诊锚点不存在");
        }
        if (isEmpty(psnNo)) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "1101 出参缺少 psn_no");
        }
        anchor.setPsnNo(psnNo.trim());
        if (isNotEmpty(injureAffirmId)) {
            anchor.setInjureAffirmId(injureAffirmId.trim());
        }
        encounterAnchorRepository.save(anchor);
    }

    /**
     * 【9102】或 UI 手动绑定 qualification_id。
     *
     * @param anchor          就诊锚点
     * @param qualificationId 平台审批资格 ID
     */
    @Transactional
    public void bindQualification(YbWiEncounterAnchor anchor, String qualificationId) {
        if (anchor == null) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "就诊锚点不存在");
        }
        if (isEmpty(qualificationId)) {
            throw new BusinessException(WiErrorCodes.INVALID_INVOKE_SESSION, "qualification_id 不能为空");
        }
        anchor.setQualificationId(qualificationId.trim());
        anchor.setQualificationStatus(WorkInjuryConstants.QUALIFICATION_VALID);
        encounterAnchorRepository.save(anchor);
    }

    /**
     * 【2301】submit 后：写入 qualification_id（若有）并置 PENDING_9102（WI-004 待再 9102）。
     *
     * @param anchor          就诊锚点
     * @param qualificationId 2301 出参 qualification_id（可空）
     */
    @Transactional
    public void apply2301Submit(YbWiEncounterAnchor anchor, String qualificationId) {
        if (anchor == null) {
            return;
        }
        if (isNotEmpty(qualificationId)) {
            anchor.setQualificationId(qualificationId.trim());
        }
        anchor.setQualificationStatus(WorkInjuryConstants.QUALIFICATION_PENDING_9102);
        encounterAnchorRepository.save(anchor);
    }

    /**
     * 【2201】submit 后标记平台已登记。
     *
     * @param anchor 就诊锚点
     */
    @Transactional
    public void markPlatformRegistered(YbWiEncounterAnchor anchor) {
        if (anchor == null) {
            return;
        }
        anchor.setPlatformRegistered(Boolean.TRUE);
        encounterAnchorRepository.save(anchor);
    }

    /**
     * 【2204】/【2205】submit 后更新 has_active_fee_upload（2205 条件插入依据）。
     *
     * @param anchor 就诊锚点
     * @param active true 表示存在未撤销的 2204 批次
     */
    @Transactional
    public void markActiveFeeUpload(YbWiEncounterAnchor anchor, boolean active) {
        if (anchor == null) {
            return;
        }
        anchor.setHasActiveFeeUpload(active);
        encounterAnchorRepository.save(anchor);
    }

    /**
     * 【2207】submit 后回写 setl_id 到 anchor。
     *
     * @param anchor 就诊锚点
     * @param setlId 2207 出参 setl_id
     */
    @Transactional
    public void apply2207SetlId(YbWiEncounterAnchor anchor, String setlId) {
        if (anchor == null || isEmpty(setlId)) {
            return;
        }
        anchor.setSetlId(setlId.trim());
        encounterAnchorRepository.save(anchor);
    }

    /**
     * 【2208】submit 后清除 anchor.setl_id。
     *
     * @param anchor 就诊锚点
     */
    @Transactional
    public void clearSetlIdAfter2208(YbWiEncounterAnchor anchor) {
        if (anchor == null) {
            return;
        }
        anchor.setSetlId(null);
        encounterAnchorRepository.save(anchor);
    }

    /**
     * 将 WiSettleContext 非空字段合并进 anchor（不覆盖已有 qualification 状态机字段）。
     *
     * @param anchor 待更新锚点
     * @param ctx    HIS 上下文
     */
    private void mergeFromContext(YbWiEncounterAnchor anchor, WiSettleContext ctx) {
        if (ctx == null) {
            return;
        }
        if (isNotEmpty(ctx.getIptOtpNo())) {
            anchor.setIptOtpNo(ctx.getIptOtpNo().trim());
        }
        if (isNotEmpty(ctx.getPsnNo())) {
            anchor.setPsnNo(ctx.getPsnNo().trim());
        }
        if (isNotEmpty(ctx.getQualificationId())) {
            anchor.setQualificationId(ctx.getQualificationId().trim());
        }
        if (isNotEmpty(ctx.getInjureAffirmId())) {
            anchor.setInjureAffirmId(ctx.getInjureAffirmId().trim());
        }
        if (isNotEmpty(ctx.getAdmDiagCode())) {
            anchor.setAdmDiagCode(ctx.getAdmDiagCode().trim());
        }
        if (isNotEmpty(ctx.getMedType())) {
            anchor.setMedType(ctx.getMedType().trim());
        }
    }
}
