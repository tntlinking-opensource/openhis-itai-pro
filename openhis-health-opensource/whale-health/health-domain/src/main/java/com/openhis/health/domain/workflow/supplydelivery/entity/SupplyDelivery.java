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

package com.openhis.health.domain.workflow.supplydelivery.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 供应发放聚合根。
 *
 * <p>对应表：{@code wkf_supply_delivery}。</p>
 *
 * <p>边界：本聚合只记录发放事实与状态推进；库存增减由应用层编排调用 InventoryItem 完成。</p>
 *
 * <p>{@code type_enum}、{@code status_enum} 等与 {@link com.openhis.health.domain.share.enums.DispenseStatus} / 供应侧类型枚举的持久化
 * {@code getCode()} 对齐（B.10.8.1）；事件载荷携带的亦为 code 字符串。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "wkf_supply_delivery")
public class SupplyDelivery extends BaseAuditedEntity {

    /** 关联的供应请求行主键 {@code request_id}。 */
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "summary_id")
    private Long summaryId;

    /** 发放状态持久化码（如 {@link DispenseStatus#getCode()}，B.10.8.1）。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    @Column(name = "patient_id")
    private Long patientId;

    /** 发放类型/业务子类型持久化码（与命令及事件载荷一致，B.10.8.1）。 */
    @Column(name = "type_enum", nullable = false)
    private String typeEnum;

    @Column(name = "item_table")
    private String itemTable;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "based_on_table")
    private String basedOnTable;

    @Column(name = "based_on_ids")
    private String basedOnIds;

    @Column(name = "unit_code")
    private String unitCode;

    @Column(name = "quantity")
    private BigDecimal quantity;

    @Column(name = "condition")
    private String condition;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(name = "trace_no", length = 50000)
    private String traceNo;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "practitioner_id")
    private Long practitionerId;

    @Column(name = "occurrence_time")
    private LocalDateTime occurrenceTime;

    @Column(name = "receiver_id")
    private Long receiverId;

    @Column(name = "receive_time")
    private LocalDateTime receiveTime;

    /**
     * 数量非零断言（BR-SDL-001）。
     *
     * @throws BusinessException 参数非法
     */
    public void assertQuantityNonZero() {
        if (this.quantity == null || this.quantity.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_DELIVERY_INVALID_ARGUMENT, "发放数量 quantity 不得为 0");
        }
    }

    /**
     * 初始化普通路径完成态（BR-SDL-003；对应 DispenseStatus.PART_COMPLETED）。
     */
    public void initCompleted() {
        // DOC-CONFLICT: DM-WKF-005/007 status 6 对应 PART_COMPLETED；如语义为 COMPLETED(4) 请对照 DM §2.3 修正枚举映射。
        this.statusEnum = DispenseStatus.PART_COMPLETED.getCode();
    }

    /**
     * 初始化汇总路径首态（BR-SDL-004）。
     *
     * @param draftOrPreparation 允许为 DRAFT(1) 或 PREPARATION(2)
     */
    public void initDraftOrPreparation(String draftOrPreparation) {
        if (draftOrPreparation == null) {
            this.statusEnum = "DRAFT";
            return;
        }
        this.statusEnum = draftOrPreparation;
    }

    /**
     * 批量推进到完成态。
     *
     * @throws BusinessException 状态不允许
     */
    public void assertCompletable() {
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_DELIVERY_INVALID_ARGUMENT, "发放状态不能为空");
        }
        // 终态校验：PART_COMPLETED/ON_HOLD/STOPPED/DECLINED 均不可再次推进完成。
        if (DispenseStatus.PART_COMPLETED.sameAs(this.statusEnum)
            || DispenseStatus.ON_HOLD.sameAs(this.statusEnum)
            || DispenseStatus.STOPPED.sameAs(this.statusEnum)
            || DispenseStatus.DECLINED.sameAs(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_DELIVERY_INVALID_ARGUMENT, "终态发放不可再次推进完成");
        }
    }

    /**
     * 推进到完成语义状态（当前映射为部分完成），并写入发生时间与操作医务人员。
     *
     * @param completedTime 完成/发生时间
     * @param practitionerId 操作医务人员主键
     */
    public void toCompleted(LocalDateTime completedTime, Long practitionerId) {
        // DOC-CONFLICT: 同 initCompleted() 注释，status 6 = PART_COMPLETED；请对照 DM §2.3 确认映射。
        this.statusEnum = DispenseStatus.PART_COMPLETED.getCode();
        if (completedTime != null) {
            this.occurrenceTime = completedTime;
        }
        if (practitionerId != null) {
            this.practitionerId = practitionerId;
        }
    }
}
