/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.paymentreconciliation.support;

import com.zxplat.domain.financial.paymentreconciliation.PaymentYbSettlementStatus;
import com.zxplat.domain.financial.paymentreconciliation.command.PaymentYbSettlementItemCommand;
import com.zxplat.domain.financial.paymentreconciliation.entity.PaymentYbSettlement;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.PaymentYbSettlementQueryModel;
import com.zxplat.domain.share.events.financial.paymentreconciliation.PaymentYbSettlementEventItem;
import com.zxplat.framework.core.application.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** 医保结算凭证行工具。 */
public final class PaymentYbSettlementSupport {

    private PaymentYbSettlementSupport() {
    }

    public static List<PaymentYbSettlementItemCommand> optionalConfirmedItem(String setlId) {
        if (StringUtils.isEmpty(setlId)) {
            return List.of();
        }
        PaymentYbSettlementItemCommand item = new PaymentYbSettlementItemCommand();
        item.setSetlId(setlId.trim());
        item.setStatus(PaymentYbSettlementStatus.CONFIRMED);
        return List.of(item);
    }

    /**
     * 退费/反结逆向单：按原单桥表拷贝同 setl 行，状态统一为 {@link PaymentYbSettlementStatus#REVOKED}。
     *
     * <p>供日结按 payment 行级过滤医保个账/统筹，避免逆向无桥表时被「空桥表兼容」虚计入负值。</p>
     */
    public static List<PaymentYbSettlementItemCommand> copyAsRevoked(List<PaymentYbSettlementQueryModel> rows) {
        List<PaymentYbSettlementItemCommand> result = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        LinkedHashSet<String> seenSetlIds = new LinkedHashSet<>();
        for (PaymentYbSettlementQueryModel row : rows) {
            if (row == null || StringUtils.isEmpty(row.getSetlId())) {
                continue;
            }
            String setlId = row.getSetlId().trim();
            if (!seenSetlIds.add(setlId)) {
                continue;
            }
            PaymentYbSettlementItemCommand item = new PaymentYbSettlementItemCommand();
            item.setSetlId(setlId);
            item.setChrgBchno(StringUtils.trimToNull(row.getChrgBchno()));
            item.setMdtrtId(StringUtils.trimToNull(row.getMdtrtId()));
            item.setSettlementRecordId(row.getSettlementRecordId());
            item.setAccountId(row.getAccountId());
            item.setStatus(PaymentYbSettlementStatus.REVOKED);
            result.add(item);
        }
        return result;
    }

    public static boolean hasConfirmed(List<PaymentYbSettlementQueryModel> rows) {
        return !confirmedSetlIds(rows).isEmpty();
    }

    public static List<String> confirmedSetlIds(List<PaymentYbSettlementQueryModel> rows) {
        List<String> setlIds = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return setlIds;
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (PaymentYbSettlementQueryModel row : rows) {
            if (row == null || StringUtils.isEmpty(row.getSetlId())) {
                continue;
            }
            if (PaymentYbSettlementStatus.REVOKED.equals(row.getStatus())) {
                continue;
            }
            seen.add(row.getSetlId().trim());
        }
        setlIds.addAll(seen);
        return setlIds;
    }

    public static String joinSetlIds(List<PaymentYbSettlementQueryModel> rows) {
        List<String> setlIds = confirmedSetlIds(rows);
        if (setlIds.isEmpty()) {
            return null;
        }
        return String.join(",", setlIds);
    }

    public static String firstMdtrtId(List<PaymentYbSettlementQueryModel> rows) {
        if (rows == null) {
            return null;
        }
        for (PaymentYbSettlementQueryModel row : rows) {
            if (row == null || PaymentYbSettlementStatus.REVOKED.equals(row.getStatus())) {
                continue;
            }
            if (StringUtils.isNotEmpty(row.getMdtrtId())) {
                return row.getMdtrtId().trim();
            }
        }
        return null;
    }

    public static List<PaymentYbSettlementEventItem> toEventItems(List<PaymentYbSettlementItemCommand> items) {
        List<PaymentYbSettlementEventItem> result = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return result;
        }
        for (PaymentYbSettlementItemCommand item : items) {
            if (item == null || StringUtils.isEmpty(item.getSetlId())) {
                continue;
            }
            PaymentYbSettlementEventItem row = new PaymentYbSettlementEventItem();
            row.setSetlId(item.getSetlId().trim());
            row.setChrgBchno(StringUtils.trimToNull(item.getChrgBchno()));
            row.setMdtrtId(StringUtils.trimToNull(item.getMdtrtId()));
            row.setSettlementRecordId(item.getSettlementRecordId());
            row.setAccountId(item.getAccountId());
            if (StringUtils.isNotEmpty(item.getStatus())) {
                row.setStatus(item.getStatus().trim());
            } else {
                row.setStatus(PaymentYbSettlementStatus.CONFIRMED);
            }
            result.add(row);
        }
        return result;
    }

    public static List<PaymentYbSettlementEventItem> toEventItemsFromEntities(List<PaymentYbSettlement> rows) {
        List<PaymentYbSettlementEventItem> result = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        for (PaymentYbSettlement row : rows) {
            if (row == null || StringUtils.isEmpty(row.getSetlId())) {
                continue;
            }
            PaymentYbSettlementEventItem item = new PaymentYbSettlementEventItem();
            item.setSetlId(row.getSetlId().trim());
            item.setChrgBchno(row.getChrgBchno());
            item.setMdtrtId(row.getMdtrtId());
            item.setSettlementRecordId(row.getSettlementRecordId());
            item.setAccountId(row.getAccountId());
            item.setStatus(row.getStatus());
            result.add(item);
        }
        return result;
    }
}
