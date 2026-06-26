package com.openhis.whale.einvoice.boss.domain.document.entity;

import com.openhis.whale.einvoice.boss.domain.support.EinvBossTenantRowEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 博思报文明细行（einv_boss_line）。
 *
 * <p>出站金额以 {@link #amount} 为准；调额后不得被现场重算覆盖。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "einv_boss_line")
public class EinvBossLine extends EinvBossTenantRowEntity {

    /** 所属单据主键。 */
    @Column(name = "document_id", nullable = false)
    private Long documentId;

    /** 明细类型（收费/挂号等）。 */
    @Column(name = "line_type", nullable = false, length = 10)
    private String lineType;

    /** 行号（从 1 起）。 */
    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    /** 博思收费项目编码。 */
    @Column(name = "charge_code", nullable = false, length = 50)
    private String chargeCode;

    /** 博思收费项目名称。 */
    @Column(name = "charge_name", nullable = false, length = 200)
    private String chargeName;

    /** 标准金额。 */
    @Column(name = "std_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal stdAmount;

    /** 实际开票金额。 */
    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** 数量。 */
    @Column(name = "quantity", precision = 14, scale = 4)
    private BigDecimal quantity;

    /** 计量单位。 */
    @Column(name = "unit", length = 20)
    private String unit;

    /** 来源 health 收费项主键。 */
    @Column(name = "source_charge_item_id")
    private Long sourceChargeItemId;

    /** 是否经人工调额。 */
    @Column(name = "adjusted", nullable = false)
    private Boolean adjusted;

    /** 调额原因。 */
    @Column(name = "adjust_reason", length = 500)
    private String adjustReason;
}
