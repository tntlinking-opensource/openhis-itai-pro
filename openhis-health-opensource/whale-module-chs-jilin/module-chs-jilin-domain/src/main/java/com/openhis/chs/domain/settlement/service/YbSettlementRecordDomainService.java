/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.service;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.openhis.chs.domain.settlement.model.PlatformSetlinfoFields;
import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.openhis.chs.domain.settlement.constants.ChsSettleEncounterClass;
import com.openhis.chs.domain.settlement.YbSettlementVoucherStatus;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.repository.YbSettlementRecordRepository;
import com.openhis.chs.domain.settlement.support.SetlinfoFieldSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 正式结算回执 {@code yb_settlement_record} 持久化 */
@Service
@RequiredArgsConstructor
public class YbSettlementRecordDomainService {

    private final YbSettlementRecordRepository settlementRecordRepository;

    /**
     * 2207 成功后持久化门诊正式结算回执。
     *
     * @param pre            关联预结算快照
     * @param setlinfo       2207 输出 setlinfo
     * @param setldetailJson setldetail 数组 JSON
     * @param paymentDraftId 支付草稿/支付单 ID
     * @param msgid2207      2207 报文 msgid
     * @return 已落库结算回执
     */
    public YbSettlementRecord saveOutpatientConfirmed(
        YbClinicPreSettle pre,
        PlatformSetlinfoFields setlinfo,
        String setldetailJson,
        Long paymentDraftId,
        String msgid2207) {
        YbSettlementRecord row = new YbSettlementRecord();
        row.setContractId(pre.getContractId());
        row.setEncounterId(pre.getEncounterId());
        row.setPaymentId(paymentDraftId);
        row.setPreSettleId(pre.getId());
        row.setChrgBchno(pre.getChrgBchno());
        row.setMdtrtId(pre.getMdtrtId());
        row.setPsnNo(pre.getPsnNo());
        row.setEncounterClass(ChsSettleEncounterClass.AMB);
        row.setStatus(YbSettlementVoucherStatus.CONFIRMED);
        SetlinfoFieldSupport.applySettlementFields(row, pre, setlinfo, setldetailJson);
        applyMsgid(row, msgid2207);
        row.setTenantId(pre.getTenantId());
        row.setCreatedAt(LocalDateTime.now());
        return settlementRecordRepository.saveAndFlush(row);
    }

    /**
     * 2304 成功后持久化住院正式结算回执。
     *
     * @param pre            关联预结算快照
     * @param chrgBchno      收费批次号
     * @param setlinfo       2304 输出 setlinfo
     * @param setldetailJson setldetail 数组 JSON
     * @param paymentDraftId 支付草稿/支付单 ID
     * @param msgid2304      2304 报文 msgid
     * @return 已落库结算回执
     */
    public YbSettlementRecord saveInpatientConfirmed(
        YbInpatientPreSettle pre,
        String chrgBchno,
        PlatformSetlinfoFields setlinfo,
        String setldetailJson,
        Long paymentDraftId,
        String msgid2304) {
        YbSettlementRecord row = new YbSettlementRecord();
        row.setContractId(pre.getContractId());
        row.setEncounterId(pre.getEncounterId());
        row.setPaymentId(paymentDraftId);
        row.setPreSettleId(pre.getId());
        row.setChrgBchno(chrgBchno);
        row.setMdtrtId(pre.getMdtrtId());
        row.setPsnNo(pre.getPsnNo());
        row.setEncounterClass(ChsSettleEncounterClass.IMP);
        row.setSettleMode(pre.getSettleMode());
        row.setStatus(YbSettlementVoucherStatus.CONFIRMED);
        SetlinfoFieldSupport.applyInpatientSettlementFields(row, pre, setlinfo, setldetailJson);
        applyMsgid(row, msgid2304);
        row.setTenantId(pre.getTenantId());
        row.setCreatedAt(LocalDateTime.now());
        return settlementRecordRepository.saveAndFlush(row);
    }

    /**
     * 保存或更新结算回执实体。
     *
     * @param record 结算回执
     */
    public void save(YbSettlementRecord record) {
        settlementRecordRepository.save(record);
    }

    /**
     * 按主键与租户查询未删除结算回执。
     *
     * @param id       结算回执主键
     * @param tenantId 租户 ID
     * @return 匹配行；不存在或已删除时 empty
     */
    public java.util.Optional<YbSettlementRecord> findByIdAndTenant(Long id, Long tenantId) {
        return settlementRecordRepository.findById(id)
            .filter(row -> tenantId.equals(row.getTenantId()) && !Boolean.TRUE.equals(row.getIsDeleted()));
    }

    /**
     * 回填 2207/2304 msgid（优先入参，回落 medins_setl_id）。
     *
     * @param row   结算回执
     * @param msgid 平台报文 msgid
     */
    private static void applyMsgid(YbSettlementRecord row, String msgid) {
        if (isNotEmpty(msgid)) {
            row.setMsgid2207(msgid.trim());
        } else if (isNotEmpty(row.getMedinsSetlId())) {
            row.setMsgid2207(row.getMedinsSetlId());
        }
    }
}
