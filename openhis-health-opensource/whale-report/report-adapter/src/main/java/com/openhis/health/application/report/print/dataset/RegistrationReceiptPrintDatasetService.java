/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.print.dataset;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 挂号小票打印数据集（委托 {@link ChargeReceiptPrintDatasetService#buildRegistrationReceipt}）。
 */
@Service
@RequiredArgsConstructor
public class RegistrationReceiptPrintDatasetService {

    private final ChargeReceiptPrintDatasetService chargeReceiptPrintDatasetService;    /**
     * 组装打印数据集 root Map（供 Port / 预览 bindPath 绑定）。
     *
     * @param params 业务参数（如 encounterId、paymentId、dateFrom 等）
     * @return 数据集 root；必填参数缺失时返回空 Map
     */

    public Map<String, Object> build(Map<String, Object> params) {
        return chargeReceiptPrintDatasetService.buildRegistrationReceipt(params);
    }
}
