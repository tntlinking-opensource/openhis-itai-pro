/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.writeoff.loader;

import com.openhis.whale.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;

/**
 * 博思开具成功后回写 health 票据的端口（CQRS 写模型）。
 *
 * <p>由 bridge {@code EinvoiceInvoiceWritebackLoaderImpl} 实现，将博思票号、二维码等
 * 写入 {@code adm_invoice}，使 HIS 票据状态与博思平台对齐。</p>
 */
public interface EinvoiceInvoiceWritebackLoader {

    /**
     * 将博思开具结果回写 {@code adm_invoice}（票号、状态、电子票元数据）。
     *
     * @param invoiceId 票据主键 {@code adm_invoice.id}
     * @param result 博思返回的蓝票结果（billNo、random、pictureUrl 等）
     * @param operatorId 操作人主键；查票补偿等场景可传 {@code null}
     */
    void issueElectronicInvoice(Long invoiceId, BossInvoiceIssueResult result, Long operatorId);
}
