package com.openhis.whale.einvoice.boss.contracts.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 待补偿列表行。
 */
@Data
public class BossCompensationRowDto {

    /** 票据主键（前端 string）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long invoiceId;

    /** 单据主键（可空）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentId;

    /** 支付主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentId;

    /** 患者姓名。 */
    private String patientName;

    /** 就诊号。 */
    private String encounterBusNo;

    /** 业务线（REG/AMB）。 */
    private String issueKind;

    /** 业务线展示名。 */
    private String issueKindLabel;

    /** 支付金额。 */
    private BigDecimal paymentAmt;

    /** 业务流水号。 */
    private String busNo;

    /** 异常类型码。 */
    private String exceptionType;

    /** 异常类型展示名。 */
    private String exceptionTypeLabel;

    /** 等待分钟数。 */
    private long waitingMinutes;

    /** 任务状态（可空）。 */
    private String taskStatus;

    /** 最近错误码。 */
    private String lastErrorCode;

    /** 最近错误信息。 */
    private String lastErrorMsg;

    /** 是否可重新入队。 */
    private boolean canRequeue;

    /** 是否可立即开具。 */
    private boolean canIssueNow;

    /** 是否可流水号查票。 */
    private boolean canRecoverByBusNo;

    /** 是否可调整金额（P1 入口预留）。 */
    private boolean canAdjustAmount;
}
