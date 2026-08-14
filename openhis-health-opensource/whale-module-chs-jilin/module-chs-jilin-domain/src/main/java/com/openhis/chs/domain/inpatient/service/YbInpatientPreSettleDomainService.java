/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inpatient.service;


import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.domain.settlement.model.PlatformSetlinfoFields;
import com.openhis.chs.domain.share.enums.MidSetlFlag;
import com.openhis.chs.domain.inpatient.command.IpConfirmSettleCommand;
import com.openhis.chs.domain.inpatient.command.IpSettleContext;
import com.openhis.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.openhis.chs.domain.inpatient.repository.YbInpatientPreSettleRepository;
import com.openhis.chs.domain.settlement.service.ChargeBatchLifecycle;
import com.openhis.chs.domain.settlement.support.SetlinfoFieldSupport;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 住院预结算快{@code yb_inpatient_pre_settle} 持久化 */
@Service
@RequiredArgsConstructor
public class YbInpatientPreSettleDomainService {

    private final YbInpatientPreSettleRepository inpatientPreSettleRepository;
    private final ChargeBatchLifecycle chargeBatchLifecycle;

    /**
     * 将 2303 预结算结果写入 {@code yb_inpatient_pre_settle} 并置为 ACTIVE。
     *
     * @param context           住院结算编排上下文
     * @param mdtrtId           平台就诊 ID
     * @param setlinfo          2303 输出 setlinfo 金额字段
     * @param chargeItemIdsJson 本批次收费项 ID 列表 JSON
     * @param tenantId          租户 ID
     * @return 持久化后的预结算快照
     */
    public YbInpatientPreSettle saveActiveFrom2303(
        IpSettleContext context,
        String mdtrtId,
        PlatformSetlinfoFields setlinfo,
        String chargeItemIdsJson,
        Long tenantId) {
        YbInpatientPreSettle row = new YbInpatientPreSettle();
        row.setPaymentDraftId(context.getPaymentDraftId());
        row.setEncounterId(context.getEncounterId());
        row.setContractId(context.getContractId());
        row.setContractBusNo(context.getContractBusNo());
        row.setMdtrtId(mdtrtId);
        row.setPsnNo(context.getPsnNo());
        row.setInsuplcAdmdvs(context.getInsuplcAdmdvs());
        row.setMdtrtCertType(context.getMdtrtCertType());
        row.setMdtrtCertNo(context.getMdtrtCertNo());
        row.setInsutype(context.getInsutype());
        row.setAcctUsedFlag(context.getAcctUsedFlag());
        row.setPsnSetlway(context.getPsnSetlway());
        row.setMedType(context.getMedType());
        row.setSettleMode(context.getSettleMode());
        // 与平台入参一致：mid_setl_flag 恒为 0（本地 MID_SETTLE 仅跳过 2402，不表示平台中途）。
        row.setMidSetlFlag(MidSetlFlag.NO.getCode());
        SetlinfoFieldSupport.applyInpatientPreSettleAmountFields(row, setlinfo);
        // Pre：零售/上传分差来自 ContextLoader（平台 medfee 写入后仍保留上传口径）
        SetlinfoFieldSupport.applyInpatientRetailUploadDiff(
            row,
            context.getRetailMedfeeSumamt(),
            context.getUploadMedfeeSumamt(),
            context.getRetailUploadDiffAmt());
        row.setChargeItemIds(chargeItemIdsJson);
        row.setStatus(YbInpatientPreSettle.STATUS_ACTIVE);
        row.setTenantId(tenantId);
        row.setCreatedAt(LocalDateTime.now());
        return inpatientPreSettleRepository.save(row);
    }

    /**
     * 按 preSettleId 或 paymentDraftId 定位 ACTIVE 预结算快照（2304 确认结算前置）。
     *
     * @param command  正式结算命令
     * @param tenantId 租户 ID
     * @return 匹配的预结算快照
     */
    public YbInpatientPreSettle requireForConfirm(IpConfirmSettleCommand command, Long tenantId) {
        if (command.getPreSettleId() != null) {
            return inpatientPreSettleRepository.findFirstByIdAndTenantIdAndIsDeletedFalse(
                command.getPreSettleId(), tenantId)
                .orElseThrow(() -> new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "未找到预结算快照"));
        }
        if (command.getPaymentDraftId() != null) {
            return inpatientPreSettleRepository
                .findFirstByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                    command.getPaymentDraftId(), YbInpatientPreSettle.STATUS_ACTIVE, tenantId)
                .orElseThrow(() -> new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "未找到预结算快照"));
        }
        throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "缺少 preSettleId paymentDraftId");
    }

    /**
     * 将预结算快照标记为已结算并持久化。
     *
     * @param pre 预结算快照
     */
    public void markSettledAndSave(YbInpatientPreSettle pre) {
        chargeBatchLifecycle.markInpatientPreSettleSettled(pre);
        inpatientPreSettleRepository.save(pre);
    }

    /**
     * 将预结算快照标记为已撤销并持久化。
     *
     * @param pre 预结算快照
     */
    public void markRevokedAndSave(YbInpatientPreSettle pre) {
        chargeBatchLifecycle.markInpatientPreSettleRevoked(pre);
        inpatientPreSettleRepository.save(pre);
    }

    /**
     * 将预结算快照标记为已取消并持久化。
     *
     * @param pre 预结算快照
     */
    public void markCancelledAndSave(YbInpatientPreSettle pre) {
        chargeBatchLifecycle.markInpatientPreSettleCancelled(pre);
        inpatientPreSettleRepository.save(pre);
    }

    /**
     * 取消指定支付草稿下所有 ACTIVE 预结算快照。
     *
     * @param paymentDraftId 支付草稿 ID
     * @param tenantId       租户 ID
     */
    public void cancelActivePreSettlesForDraft(Long paymentDraftId, Long tenantId) {
        if (paymentDraftId == null) {
            return;
        }
        List<YbInpatientPreSettle> activeRows = inpatientPreSettleRepository
            .findAllByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(
                paymentDraftId, YbInpatientPreSettle.STATUS_ACTIVE, tenantId);
        for (YbInpatientPreSettle row : activeRows) {
            markCancelledAndSave(row);
        }
    }

    /**
     * 换新平台就诊后，用 2303 结果刷新仍为 ACTIVE 的预结算快照（保留 paymentDraftId / chargeItemIds）。
     *
     * @param row      ACTIVE 预结算行
     * @param mdtrtId  新平台就诊 ID
     * @param setlinfo 2303 setlinfo
     * @return 持久化后的快照
     */
    public YbInpatientPreSettle refreshActiveFrom2303(
            YbInpatientPreSettle row,
            String mdtrtId,
            PlatformSetlinfoFields setlinfo) {
        if (row == null) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "预结算快照不存在");
        }
        if (!YbInpatientPreSettle.STATUS_ACTIVE.equals(row.getStatus())) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "预结算快照状态非法，无法刷新");
        }
        row.setMdtrtId(mdtrtId);
        SetlinfoFieldSupport.applyInpatientPreSettleAmountFields(row, setlinfo);
        return inpatientPreSettleRepository.save(row);
    }

    /**
     * 按主键查找预结算快照。
     *
     * @param id 预结算快照主键
     * @return 快照实体；id 为空或未找到时返回 {@code null}
     */
    public YbInpatientPreSettle findById(Long id) {
        if (id == null) {
            return null;
        }
        return inpatientPreSettleRepository.findById(id).orElse(null);
    }

    /**
     * 按主键与租户查找预结算快照。
     *
     * @param id       预结算快照主键
     * @param tenantId 租户 ID
     * @return 快照实体；id 为空或未找到时返回空
     */
    public java.util.Optional<YbInpatientPreSettle> findOptionalByIdAndTenant(Long id, Long tenantId) {
        if (id == null) {
            return java.util.Optional.empty();
        }
        return inpatientPreSettleRepository.findFirstByIdAndTenantIdAndIsDeletedFalse(id, tenantId);
    }

    /**
     * 按支付草稿 ID 查找最新预结算快照（优先 SETTLED，其次 ACTIVE）。
     *
     * @param paymentDraftId 支付草稿 ID
     * @param tenantId       租户 ID
     * @return 最新快照；参数为空或未找到时返回 {@code null}
     */
    public YbInpatientPreSettle findLatestByPaymentDraftId(Long paymentDraftId, Long tenantId) {
        if (paymentDraftId == null) {
            return null;
        }
        Optional<YbInpatientPreSettle> settled = inpatientPreSettleRepository
            .findFirstByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                paymentDraftId, YbInpatientPreSettle.STATUS_SETTLED, tenantId);
        if (settled.isPresent()) {
            return settled.get();
        }
        return inpatientPreSettleRepository
            .findFirstByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                paymentDraftId, YbInpatientPreSettle.STATUS_ACTIVE, tenantId)
            .orElse(null);
    }
}
