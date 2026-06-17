/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.inpatient.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
/** 启动住院医保结算协同单请求。 */
@Schema(description = "启动住院医保结算协同单请求")
public class StartIpInsuranceSettleRequest {

    /** 支付草稿主键 {@code fin_payment_reconciliation.id}，须为 DRAFT 态。 */
    @NotNull
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentDraftId;

    /** 医保合同主键。 */
    @NotNull
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 医保账户主键。 */
    @NotNull
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    /** 住院就诊主键。 */
    @NotNull
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者主键。 */
    @NotNull
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 本地预结算记录主键。 */
    @NotNull
    @JsonSerialize(using = ToStringSerializer.class)
    private Long preSettleId;

    /** 医保平台预结算 id（可空，由 Port 回写）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ybPreSettleId;

    /** 本次结算收费项 id 列表。 */
    @NotNull
    private List<Long> chargeItemIds;

    /** 是否出院全结（true 表示本次为出院财务全结批次）。 */
    private Boolean dischargeFullSettle;

    /** 本批次结算前已结金额，供混合结算展示。 */
    private BigDecimal settledBeforeCurrentBatch;

    /** 账单日期，落账时写入支付单。 */
    private LocalDateTime billDate;

    /** 操作员主键。 */
    @NotNull
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;
}
