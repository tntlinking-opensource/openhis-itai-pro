/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.service;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.domain.settlement.model.PlatformSetlinfoFields;
import com.openhis.chs.domain.outpatient.command.OpConfirmSettleCommand;
import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.openhis.chs.domain.outpatient.repository.YbClinicPreSettleRepository;
import com.openhis.chs.domain.settlement.service.ChargeBatchLifecycle;
import com.openhis.chs.domain.settlement.support.SetlinfoFieldSupport;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 门诊预结算快照 {@code yb_clinic_pre_settle} 持久化 */
@Service
@RequiredArgsConstructor
public class YbClinicPreSettleDomainService {

    private final YbClinicPreSettleRepository clinicPreSettleRepository;
    private final ChargeBatchLifecycle chargeBatchLifecycle;

    /**
     * 将 2206 预结算结果写入 {@code yb_clinic_pre_settle} 并置为 ACTIVE。
     *
     * @param context           预结算编排上下文
     * @param mdtrtId           平台就诊 ID
     * @param chrgBchno         收费批次号
     * @param setlinfo          2206 输出 setlinfo 金额字段
     * @param chargeItemIdsJson 本批次收费项 ID 列表 JSON
     * @param tenantId          租户 ID
     * @return 持久化后的预结算快照
     */
    public YbClinicPreSettle saveActiveFrom2206(
        OpPreSettleCommand context,
        String mdtrtId,
        String chrgBchno,
        PlatformSetlinfoFields setlinfo,
        String chargeItemIdsJson,
        Long tenantId) {
        YbClinicPreSettle row = new YbClinicPreSettle();
        row.setContractId(context.getContractId());
        row.setEncounterId(context.getEncounterId());
        row.setPaymentDraftId(context.getPaymentDraftId());
        row.setAccountId(context.getAccountId());
        row.setChrgBchno(chrgBchno);
        row.setContractBusNo(context.getContractBusNo());
        row.setMdtrtId(mdtrtId);
        row.setPsnNo(context.getPsnNo());
        row.setInsuplcAdmdvs(context.getInsuplcAdmdvs());
        row.setMdtrtCertType(context.getMdtrtCertType());
        row.setMdtrtCertNo(context.getMdtrtCertNo());
        row.setInsutype(context.getInsutype());
        row.setAcctUsedFlag(context.getAcctUsedFlag());
        row.setPsnSetlway(context.getPsnSetlway());
        SetlinfoFieldSupport.applyPreSettleAmountFields(row, setlinfo);
        // Pre：零售/上传分差来自 ContextLoader（平台 medfee 写入后仍保留上传口径）
        SetlinfoFieldSupport.applyClinicRetailUploadDiff(
            row,
            context.getRetailMedfeeSumamt(),
            context.getUploadMedfeeSumamt(),
            context.getRetailUploadDiffAmt());
        row.setChargeItemIds(chargeItemIdsJson);
        row.setStatus(YbClinicPreSettle.STATUS_ACTIVE);
        row.setMedType(context.getMedType());
        row.setDiseCodg(context.getMainDiagCode());
        row.setTenantId(tenantId);
        row.setCreatedAt(LocalDateTime.now());
        return clinicPreSettleRepository.save(row);
    }

    /**
     * 按 chrg_bchno 或 paymentDraftId 定位 ACTIVE 预结算快照（2207 确认结算前置）。
     *
     * @param command  正式结算命令
     * @param tenantId 租户 ID
     * @return 匹配的预结算快照
     */
    public YbClinicPreSettle requireForConfirm(OpConfirmSettleCommand command, Long tenantId) {
        if (isNotEmpty(command.getChrgBchno())) {
            return clinicPreSettleRepository.findFirstByChrgBchnoAndTenantIdAndIsDeletedFalse(
                command.getChrgBchno().trim(), tenantId)
                .orElseThrow(() -> new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "未找到预结算快照"));
        }
        if (command.getPaymentDraftId() != null) {
            return clinicPreSettleRepository.findFirstByPaymentDraftIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                command.getPaymentDraftId(), tenantId)
                .orElseThrow(() -> new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "未找到预结算快照"));
        }
        throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "缺少 chrg_bchno paymentDraftId");
    }

    /**
     * 按收费批次号查找预结算快照，不存在则抛业务异常。
     *
     * @param chrgBchno 收费批次号
     * @param tenantId  租户 ID
     * @return 预结算快照
     */
    public YbClinicPreSettle requireByChrgBchno(String chrgBchno, Long tenantId) {
        YbClinicPreSettle row = findByChrgBchno(chrgBchno, tenantId);
        if (row == null) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "未找到预结算快照");
        }
        return row;
    }

    /**
     * 按收费批次号查找预结算快照。
     *
     * @param chrgBchno 收费批次号
     * @param tenantId  租户 ID
     * @return 预结算快照；不存在时返回 {@code null}
     */
    public YbClinicPreSettle findByChrgBchno(String chrgBchno, Long tenantId) {
        if (isEmpty(chrgBchno) || tenantId == null) {
            return null;
        }
        return clinicPreSettleRepository.findFirstByChrgBchnoAndTenantIdAndIsDeletedFalse(
            chrgBchno.trim(), tenantId)
            .orElse(null);
    }

    /**
     * 将预结算快照标记为已结算并持久化。
     *
     * @param pre 预结算快照
     */
    public void markSettledAndSave(YbClinicPreSettle pre) {
        chargeBatchLifecycle.markClinicPreSettleSettled(pre);
        clinicPreSettleRepository.save(pre);
    }

    /**
     * 将预结算快照改挂到新支付草稿（银联补收；不改 status）。
     *
     * @param preSettleId  预结算主键
     * @param newPaymentId 新支付草稿主键
     * @param tenantId     租户 ID
     */
    public void rebindPaymentDraftId(Long preSettleId, Long newPaymentId, Long tenantId) {
        if (preSettleId == null || newPaymentId == null || tenantId == null) {
            return;
        }
        YbClinicPreSettle pre = clinicPreSettleRepository.findById(preSettleId).orElse(null);
        if (pre == null || Boolean.TRUE.equals(pre.getIsDeleted()) || !tenantId.equals(pre.getTenantId())) {
            return;
        }
        pre.setPaymentDraftId(newPaymentId);
        pre.setUpdatedAt(LocalDateTime.now());
        clinicPreSettleRepository.save(pre);
    }

    /**
     * 将预结算快照标记为已撤销并持久化。
     *
     * @param pre 预结算快照
     */
    public void markRevokedAndSave(YbClinicPreSettle pre) {
        chargeBatchLifecycle.markClinicPreSettleRevoked(pre);
        clinicPreSettleRepository.save(pre);
    }

    /**
     * 将预结算快照标记为已取消并持久化。
     *
     * @param pre 预结算快照
     */
    public void markCancelledAndSave(YbClinicPreSettle pre) {
        chargeBatchLifecycle.markClinicPreSettleCancelled(pre);
        clinicPreSettleRepository.save(pre);
    }

    /**
     * 按批次将 ACTIVE 预结算置 CANCELLED（独立事务）。
     *
     * <p>供 2205 成功后调用：外层预结算若因后续 2204/2206 失败回滚，本地状态仍与平台一致。</p>
     *
     * @param chrgBchno 收费批次号
     * @param tenantId  租户 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCancelledByChrgBchnoInNewTx(String chrgBchno, Long tenantId) {
        if (isEmpty(chrgBchno)) {
            return;
        }
        YbClinicPreSettle pre = findByChrgBchno(chrgBchno.trim(), tenantId);
        if (pre == null || !YbClinicPreSettle.STATUS_ACTIVE.equals(pre.getStatus())) {
            return;
        }
        markCancelledAndSave(pre);
    }

    /**
     * 按主键查找预结算快照。
     *
     * @param id 预结算快照主键
     * @return 快照实体；id 为空或未找到时返回 {@code null}
     */
    public YbClinicPreSettle findById(Long id) {
        if (id == null) {
            return null;
        }
        return clinicPreSettleRepository.findById(id).orElse(null);
    }

    /**
     * 按支付草稿 ID 查找最新一条预结算快照。
     *
     * @param paymentDraftId 支付草稿 ID
     * @param tenantId       租户 ID
     * @return 最新快照；参数为空或未找到时返回 {@code null}
     */
    public YbClinicPreSettle findLatestByPaymentDraftId(Long paymentDraftId, Long tenantId) {
        if (paymentDraftId == null) {
            return null;
        }
        return clinicPreSettleRepository
            .findFirstByPaymentDraftIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(paymentDraftId, tenantId)
            .orElse(null);
    }
}
