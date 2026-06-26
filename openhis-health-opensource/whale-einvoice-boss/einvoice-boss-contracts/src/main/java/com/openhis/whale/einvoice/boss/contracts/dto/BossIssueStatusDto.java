package com.openhis.whale.einvoice.boss.contracts.dto;

import lombok.Data;

/**
 * 单张票据的开票进度读模型（收费窗/待补偿用）。
 */
@Data
public class BossIssueStatusDto {

    /** health 侧票据状态（DRAFT/ISSUED/CANCELLED）。 */
    private String invoiceStatus;

    /** 异步任务状态。 */
    private String taskStatus;

    /** 金额一致性状态。 */
    private String amountStatus;

    /** 博思票号（已开具时有值）。 */
    private String billNo;

    /** QR 是否已就绪。 */
    private boolean qrReady;
}
