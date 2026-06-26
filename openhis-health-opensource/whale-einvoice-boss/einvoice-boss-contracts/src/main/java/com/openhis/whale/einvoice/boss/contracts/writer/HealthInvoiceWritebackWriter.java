package com.openhis.whale.einvoice.boss.contracts.writer;

import com.openhis.whale.einvoice.boss.contracts.dto.BossInvoiceIssueResult;

/**
 * 博思开具成功后回写 health 票据（adapter 实现）。
 */
public interface HealthInvoiceWritebackWriter {

    /**
     * 将博思开具结果回写 adm_invoice。
     *
     * @param invoiceId 票据主键
     * @param result 博思开具结果
     * @param operatorId 操作人（可空）
     */
    void issueElectronicInvoice(Long invoiceId, BossInvoiceIssueResult result, Long operatorId);
}
