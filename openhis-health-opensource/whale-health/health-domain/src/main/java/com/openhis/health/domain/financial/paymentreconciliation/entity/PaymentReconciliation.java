/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.financial.paymentreconciliation.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.converter.PaymentKindConverter;
import com.openhis.health.domain.share.converter.PaymentStatusConverter;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水聚合根。
 */
@Getter
@Setter
@Entity
@Table(name = "fin_payment_reconciliation")
public class PaymentReconciliation extends BaseAuditedEntity {
    /** status 枚举值。 */
    @Column(name = "status_enum", nullable = false)
    @Convert(converter = PaymentStatusConverter.class)
    private PaymentStatus statusEnum;
    /** relation 标识。 */
    @Column(name = "relation_id")
    private Long relationId;
    /** payment 编号。 */
    @Column(name = "payment_no", nullable = false)
    private String paymentNo;
    /** payment 枚举值。 */
    @Column(name = "payment_enum", nullable = false)
    private String paymentEnum;
    /** kind 枚举值。 */
    @Column(name = "kind_enum", nullable = false)
    @Convert(converter = PaymentKindConverter.class)
    private PaymentKind kindEnum;
    /** enterer 标识。 */
    @Column(name = "enterer_id", nullable = false)
    private Long entererId;
    /** issuer 枚举值。 */
    @Column(name = "issuer_enum")
    private String issuerEnum;
    /** patient 标识。 */
    @Column(name = "patient_id", nullable = false)
    private Long patientId;
    /** practitioner 标识。 */
    @Column(name = "practitioner_id")
    private Long practitionerId;
    /** outcome 枚举值。 */
    @Column(name = "outcome_enum")
    private String outcomeEnum;
    /** location 标识。 */
    @Column(name = "location_id")
    private Long locationId;
    /** expirationDate。 */
    @Column(name = "expiration_date")
    private LocalDateTime expirationDate;
    /** tendered 金额（元，与库 numeric(19,6) 一致）。 */
    @Column(name = "tendered_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal tenderedAmount;
    /** returned 金额。 */
    @Column(name = "returned_amount", precision = 19, scale = 6)
    private BigDecimal returnedAmount;
    /** display 金额。 */
    @Column(name = "display_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal displayAmount;
    /** print 数量。 */
    @Column(name = "print_count", nullable = false)
    private Integer printCount;
    /** contract 编号。 */
    @Column(name = "contract_no")
    private String contractNo;
    /** chargeItemIds。 */
    @Column(name = "charge_item_ids", nullable = false)
    private String chargeItemIds;
    /** encounter 标识。 */
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;
    /** billDate。 */
    @Column(name = "bill_date")
    private LocalDateTime billDate;
    /** invoice 标识。 */
    @Column(name = "invoice_id")
    private Long invoiceId;
    /** account 标识。 */
    @Column(name = "account_id")
    private Long accountId;
    /** ybSettleIds。 */
    @Column(name = "yb_settle_ids")
    private String ybSettleIds;
    /** ybClear 标志位。 */
    @Column(name = "yb_clear_flag")
    private Boolean ybClearFlag;

    /**
      * 断言业务前置条件满足。
     */
    public void assertChargeItemsNotEmpty() {
        if (this.kindEnum == null) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "支付大类不能为空");
        }
        if (!this.kindEnum.allowsEmptyChargeItemIds()) {
            if (this.chargeItemIds == null || this.chargeItemIds.isBlank()) {
                throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT, "非住院预交金收取场景收费项不能为空");
            }
        }
    }

    /**
      * 断言业务前置条件满足。
     */
    public void assertAmountNonNegative() {
        if (this.tenderedAmount == null || this.tenderedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "支付金额不能为负数");
        }
    }

    /**
     * 初始化新建主单的领域默认值：草稿态、打印次数零、医保清算标记为否。
     *
     * <p>凡新建 {@link PaymentReconciliation} 并落库的路径（含逆向退款单在覆盖为 {@link PaymentStatus#REFUND_PROCESSING} 之前）
     * 均应调用本方法，以满足 {@code print_count}/{@code yb_clear_flag} 等列非空约束。</p>
     */
    public void initDraft() {
        this.statusEnum = PaymentStatus.DRAFT;
        this.printCount = 0;
        this.ybClearFlag = Boolean.FALSE;
    }

    /**
     * 将收费项 ID 串规范为可写入 {@code charge_item_ids} 非空列的值（null 视为无关联项，落库空串）。
     *
     * @param raw 命令或上游入参，可为 null
     * @return 非 null 字符串
     */
    public static String coerceChargeItemIds(String raw) {
        if (raw == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        return raw;
    }

    /**
     * 断言当前内存态已具备新建或更新后落库所需的非空头字段（与 {@code fin_payment_reconciliation} 及映射一致）。
     *
     * <p>新建路径须在 {@link #initDraft()}（及命令对状态的覆盖）之后调用；
     * 更新路径在已加载聚合上改写字段后亦可调用以复用同一套非空门禁。</p>
     *
     * @throws BusinessException 任一必填头字段缺失
     */
    public void assertHeaderCompleteBeforeNewPersist() {
        if (this.encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "支付单就诊标识不能为空");
        }
        if (this.patientId == null) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "支付单患者标识不能为空");
        }
        if (this.entererId == null) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "支付单经办人标识不能为空");
        }
        if (this.kindEnum == null) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "支付单支付大类不能为空");
        }
        if (StringUtils.isBlank(this.paymentEnum)) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "支付单支付渠道枚举不能为空");
        }
        if (this.tenderedAmount == null) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "支付单实收金额不能为空");
        }
        if (this.displayAmount == null) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "支付单展示金额不能为空");
        }
        if (StringUtils.isBlank(this.paymentNo)) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "支付单业务编号不能为空");
        }
        if (this.chargeItemIds == null) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "支付单收费项串不能为未赋值 null");
        }
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "支付单状态不能为空");
        }
        if (this.printCount == null) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "支付单打印次数不能为空");
        }
    }

    /**
     * 断言当前状态允许被当作目标状态进行迁移校验（与命令侧期望状态比对）。
     *
     * @param expectedStatus 调用方期望的当前状态
     * @throws BusinessException 当前状态与期望不一致
     */
    public void assertStatusTransitionAllowed(PaymentStatus expectedStatus) {
        if (!expectedStatus.equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_STATUS, "支付状态不允许当前操作");
        }
    }

    /**
     * 断言当前支付单处于可发起退款申请状态（SUCCESS 或 PARTIAL_REFUNDED）。
     *
     * <p>对应 DM §6.1 退款受理方法的前置条件；仅成功收款或部分退款状态允许进入退款流程。</p>
     *
     * @throws BusinessException 状态不满足退款前置条件
     */
    public void assertRefundRequestable() {
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_STATUS, "仅成功或部分退款状态可进入退款受理");
        }
        if (!PaymentStatus.SUCCESS.equals(this.statusEnum)
            && !PaymentStatus.PARTIAL_REFUNDED.equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_STATUS, "仅成功或部分退款状态可进入退款受理");
        }
    }
}
