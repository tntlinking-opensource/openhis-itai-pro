/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inpatient.support;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;

import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.plugin.chs.bridge.inpatient.invoker.Ip2302Invoker;
import com.zxplat.plugin.chs.bridge.settlement.support.ChsFeeDetailBatchSupport;
import com.zxplat.plugin.chs.domain.inpatient.command.IpSettleContext;
import com.zxplat.plugin.chs.domain.inpatient.entity.YbInpatientFeeUpload;
import com.zxplat.plugin.chs.domain.inpatient.service.YbInpatientFeeUploadQueryService;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院预结算前清扫「院内已失效、平台仍 UPLOADED」的孤儿费用明细。
 *
 * <p>典型场景：中途预结算 2301 成功后，护士取消划价/软删费用但未走 2302，
 * 再预结算时平台费用明细总额大于本次 {@code medfee_sumamt}，报
 * {@code iptPsnSetlMgtBO_3139}。</p>
 */
@Component
@RequiredArgsConstructor
public class IpOrphanUploadedFeeRevokeSupport {

    private final YbInpatientFeeUploadQueryService feeUploadQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final Ip2302Invoker ip2302Invoker;

    /**
     * 对当前就诊号下已失效的 UPLOADED 行出站 2302，并本地标 REVOKED。
     *
     * @param context       住院结算上下文（须含 encounterId、psnNo）
     * @param mdtrtId       当前平台就诊号；仅清扫该号下上传行
     * @param invokeContext 出站上下文
     * @param tenantId      租户 ID
     * @return 本次撤销的上传行数
     */
    public int revokeOrphansBeforePreSettle(
            IpSettleContext context,
            String mdtrtId,
            InvokeContext invokeContext,
            Long tenantId) {
        // Gate：缺就诊/就诊号/人员编号则无法 2302
        if (context == null || context.getEncounterId() == null || tenantId == null) {
            return 0;
        }
        if (isEmpty(mdtrtId) || isEmpty(context.getPsnNo()) || isEmpty(context.getContractBusNo())) {
            return 0;
        }
        List<YbInpatientFeeUpload> uploaded =
            feeUploadQueryService.listUploadedByEncounter(context.getEncounterId(), tenantId);
        // Gate：无已上传行
        if (uploaded.isEmpty()) {
            return 0;
        }
        String mdtrtTrim = mdtrtId.trim();
        List<YbInpatientFeeUpload> sameMdtrt = new ArrayList<>();
        Set<Long> chargeItemIds = new HashSet<>();
        for (YbInpatientFeeUpload row : uploaded) {
            if (row == null || isEmpty(row.getMdtrtId())) {
                continue;
            }
            // Pre：只处理当前平台就诊号，避免误撤历史死号残留
            if (!mdtrtTrim.equals(row.getMdtrtId().trim())) {
                continue;
            }
            sameMdtrt.add(row);
            if (row.getChargeItemId() != null) {
                chargeItemIds.add(row.getChargeItemId());
            }
        }
        // Gate：本就诊号下无 UPLOADED
        if (sameMdtrt.isEmpty()) {
            return 0;
        }
        Map<Long, ChargeItem> chargeById = this.loadChargeById(chargeItemIds);
        String pendingCode = ChargeItemStatus.PENDING_SETTLEMENT.getCode();
        List<YbInpatientFeeUpload> orphans = new ArrayList<>();
        for (YbInpatientFeeUpload row : sameMdtrt) {
            if (this.isOrphanUpload(row, chargeById, pendingCode)) {
                orphans.add(row);
            }
        }
        // Gate：无孤儿则不调平台
        if (orphans.isEmpty()) {
            return 0;
        }
        int revoked = 0;
        List<List<YbInpatientFeeUpload>> batches = ChsFeeDetailBatchSupport.partition(
            orphans, ChsFeeDetailBatchSupport.PLATFORM_MAX_FEEDTAIL_ROWS);
        for (List<YbInpatientFeeUpload> batch : batches) {
            revoked += ip2302Invoker.revokeUploadedRows(
                context.getContractBusNo(),
                mdtrtTrim,
                context.getPsnNo().trim(),
                batch,
                invokeContext).getRevokedCount();
        }
        return revoked;
    }

    /**
     * 批量加载收费项，供判断孤儿。
     *
     * <p>软删行可能被仓储过滤而不出现在结果中——此时按「缺失」视为孤儿。</p>
     *
     * @param chargeItemIds 收费项主键集合
     * @return id → 实体；找不到的键不出现
     */
    private Map<Long, ChargeItem> loadChargeById(Set<Long> chargeItemIds) {
        Map<Long, ChargeItem> map = new HashMap<>();
        if (chargeItemIds == null || chargeItemIds.isEmpty()) {
            return map;
        }
        ChargeItemQueryCommand query = new ChargeItemQueryCommand();
        query.setIds(new ArrayList<>(chargeItemIds));
        List<ChargeItem> items = chargeItemQueryService.loadChargeItemsByIds(query);
        if (items == null || items.isEmpty()) {
            return map;
        }
        for (ChargeItem item : items) {
            if (item == null || item.getId() == null) {
                continue;
            }
            map.put(item.getId(), item);
        }
        return map;
    }

    /**
     * 判定上传行是否为孤儿：收费项缺失、软删、或已非待结算。
     *
     * @param row         上传行
     * @param chargeById  收费项索引
     * @param pendingCode {@link ChargeItemStatus#PENDING_SETTLEMENT} code
     * @return 孤儿为 true
     */
    private boolean isOrphanUpload(
            YbInpatientFeeUpload row,
            Map<Long, ChargeItem> chargeById,
            String pendingCode) {
        if (row == null || row.getChargeItemId() == null) {
            return true;
        }
        ChargeItem item = chargeById.get(row.getChargeItemId());
        // 库中已无此收费项 → 孤儿
        if (item == null) {
            return true;
        }
        // 软删后平台仍留底 → 孤儿
        if (Boolean.TRUE.equals(item.getIsDeleted())) {
            return true;
        }
        String status = item.getStatusEnum();
        // 已结算/退费等终态不应再参与本次预结总额
        if (isEmpty(status)) {
            return true;
        }
        if (!pendingCode.equals(status.trim())) {
            return true;
        }
        // 仍待结算且未删：保留平台明细，与本次 medfee 勾稽
        return false;
    }
}
