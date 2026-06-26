package com.openhis.whale.einvoice.boss.contracts.client;

import com.openhis.whale.einvoice.boss.contracts.dto.BossInvoiceIssueResult;

import java.util.Optional;

/**
 * 博思电子票据对外 Client（health 经事件间接触发；application 提供 @Primary 实现）。
 */
public interface BossElectronicInvoiceClient {

    /**
     * 异步入队：创建 document/line/task，不阻塞支付事务。
     *
     * @param invoiceId health 侧票据主键
     */
    void scheduleIssueAsync(Long invoiceId);

    /**
     * 按单据同步调博思开具（Worker / 手工补偿）。
     *
     * @param documentId boss 单据主键
     * @return 开具结果
     */
    BossInvoiceIssueResult issueByDocument(Long documentId);

    /**
     * 异步入队冲红任务。
     *
     * @param invoiceId 票据主键
     * @param reason 冲红原因
     */
    void scheduleWriteOffAsync(Long invoiceId, String reason);

    /**
     * 同步冲红。
     *
     * @param invoiceId 票据主键
     * @param reason 冲红原因
     * @return 冲红结果
     */
    BossInvoiceIssueResult writeOff(Long invoiceId, String reason);

    /**
     * 按 busNo 向博思查票补偿（超时/丢单场景）。
     *
     * @param busNo 业务流水号
     * @return 查票成功时的开具结果
     */
    Optional<BossInvoiceIssueResult> recoverByBusNo(String busNo);
}
