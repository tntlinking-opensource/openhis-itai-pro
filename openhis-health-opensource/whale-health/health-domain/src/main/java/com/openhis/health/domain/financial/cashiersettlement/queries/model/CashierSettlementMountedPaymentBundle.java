/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.cashiersettlement.queries.model;

import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 交账头挂载 payment 一次装载结果（预览行 + 读模型），避免头表双扫。
 */
@Getter
@Setter
public class CashierSettlementMountedPaymentBundle {

    /** 与日结预览行同构（含逆向净额对齐）。 */
    private List<UnsettledPaymentPreviewItemQueryModel> previewItems = new ArrayList<>();

    /** payment 读模型（含医保关联）。 */
    private List<PaymentReconciliationQueryModel> paymentModels = new ArrayList<>();
}
