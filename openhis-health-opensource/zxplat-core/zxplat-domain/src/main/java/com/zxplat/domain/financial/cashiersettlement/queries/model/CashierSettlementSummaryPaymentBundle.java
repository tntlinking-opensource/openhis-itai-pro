/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.cashiersettlement.queries.model;

import com.zxplat.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 日结展示窗 payment 一次装载结果（含已交账预览行 + 读模型），避免展示窗双扫。
 */
@Getter
@Setter
public class CashierSettlementSummaryPaymentBundle {

    /** 展示口径预览行（含已交账）。 */
    private List<UnsettledPaymentPreviewItemQueryModel> previewItems = new ArrayList<>();

    /** 展示口径读模型（含医保关联）。 */
    private List<PaymentReconciliationQueryModel> paymentModels = new ArrayList<>();
}
