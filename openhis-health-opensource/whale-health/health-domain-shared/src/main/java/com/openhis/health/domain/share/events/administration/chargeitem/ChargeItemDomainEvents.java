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

package com.openhis.health.domain.share.events.administration.chargeitem;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

public final class ChargeItemDomainEvents {
    /**
     * 处理逻辑。
     * @return 返回结果
     */
    private ChargeItemDomainEvents() {
    }

    /**
     * 挂号类收费明细已创建事件：aggregateId 为 {@code adm_charge_item.id}。
     *
     * <p>除框架维度外必须携带 {@link #encounterId} 与 {@link #totalPrice}，
     * 供就诊生命周期投影器将应收累加至门诊挂号列表与挂号单读模型；
     * 发布前由收费明细写侧从已落库行写入本载荷，避免仅靠聚合主键导致投影侧无法解析就诊锚点。</p>
     */
    @Getter
    @Setter
    public static class RegistrationChargeItemCreatedEvent extends DomainEvent {

        /** 关联就诊主键，与 {@code adm_charge_item.encounter_id} 一致；缺省或 0 时投影器忽略金额累加。 */
        private Long encounterId;

        /** 本条明细金额（与 {@code adm_charge_item.total_price} 一致），挂号列表按该值累加 {@code account_amount}。 */
        private BigDecimal totalPrice;

        /** 无参构造器（反序列化用）。 */
        public RegistrationChargeItemCreatedEvent() {
            super();
        }

        /**
         * 构造器：仅设置聚合类型与 ChargeItem 主键。
         *
         * @param id 收费明细主键
         */
        public RegistrationChargeItemCreatedEvent(Long id) {
            super(id, AggregateTypeConstants.CHARGE_ITEM);
        }
    }
    @Getter @Setter public static class OrderDraftChargeItemCreatedEvent extends DomainEvent { public OrderDraftChargeItemCreatedEvent() { super(); } public OrderDraftChargeItemCreatedEvent(Long id) { super(id, AggregateTypeConstants.CHARGE_ITEM); } }
    @Getter @Setter public static class NursePricingChargeItemCreatedEvent extends DomainEvent { public NursePricingChargeItemCreatedEvent() { super(); } public NursePricingChargeItemCreatedEvent(Long id) { super(id, AggregateTypeConstants.CHARGE_ITEM); } }
    /** 住院滚费收费明细已创建（来源 auto-roll-fees；aggregateId 为 adm_charge_item.id）。 */
    @Getter @Setter public static class AutoRollChargeItemCreatedEvent extends DomainEvent { public AutoRollChargeItemCreatedEvent() { super(); } public AutoRollChargeItemCreatedEvent(Long id) { super(id, AggregateTypeConstants.CHARGE_ITEM); } }
    @Getter @Setter public static class NursePricingChargeItemRemovedEvent extends DomainEvent {
        /** 关联就诊标识。 */
        private Long encounterId;
        public NursePricingChargeItemRemovedEvent() { super(); }
        public NursePricingChargeItemRemovedEvent(Long id, Long encounterId) {
            super(id, AggregateTypeConstants.CHARGE_ITEM);
            this.encounterId = encounterId;
        }
    }
    @Getter @Setter public static class ChargeItemActivatedByExecutionEvent extends DomainEvent {
        /** 执行记录主键。 */
        private Long procedureId;
        public ChargeItemActivatedByExecutionEvent() { super(); }
        public ChargeItemActivatedByExecutionEvent(Long id) { super(id, AggregateTypeConstants.CHARGE_ITEM); }
    }
    @Getter @Setter public static class ChargeItemExecutionCancelledEvent extends DomainEvent { public ChargeItemExecutionCancelledEvent() { super(); } public ChargeItemExecutionCancelledEvent(Long id) { super(id, AggregateTypeConstants.CHARGE_ITEM); } }
    @Getter @Setter public static class ChargeItemSettledEvent extends DomainEvent { public ChargeItemSettledEvent() { super(); } public ChargeItemSettledEvent(Long id) { super(id, AggregateTypeConstants.CHARGE_ITEM); } }
    /**
     * 挂号类收费明细已退费事件：aggregateId 为 {@code adm_charge_item.id}。
     *
     * <p>须携带 {@link #encounterId}，供投影器将挂号列表与挂号单上的应收摘要清零。</p>
     */
    @Getter
    @Setter
    public static class RegistrationChargeItemRefundedEvent extends DomainEvent {

        /** 关联就诊主键，与退费前收费明细上的 encounter 一致。 */
        private Long encounterId;

        /** 无参构造器（反序列化用）。 */
        public RegistrationChargeItemRefundedEvent() {
            super();
        }

        /**
         * 构造器。
         *
         * @param id 收费明细主键
         */
        public RegistrationChargeItemRefundedEvent(Long id) {
            super(id, AggregateTypeConstants.CHARGE_ITEM);
        }
    }
    @Getter @Setter public static class ChargeItemAccountSwitchedEvent extends DomainEvent { private Long newAccountId; public ChargeItemAccountSwitchedEvent() { super(); } public ChargeItemAccountSwitchedEvent(Long id, Long newAccountId) { super(id, AggregateTypeConstants.CHARGE_ITEM); this.newAccountId = newAccountId; } }
    @Getter @Setter public static class ChargeItemPreSettledEvent extends DomainEvent { private Integer size; public ChargeItemPreSettledEvent() { super(); } public ChargeItemPreSettledEvent(Long id, Integer size) { super(id, AggregateTypeConstants.CHARGE_ITEM); this.size = size; } }
    @Getter @Setter public static class OutpatientBillRefundRequestedEvent extends DomainEvent { private List<Long> ids; public OutpatientBillRefundRequestedEvent() { super(); } public OutpatientBillRefundRequestedEvent(Long id, List<Long> ids) { super(id, AggregateTypeConstants.CHARGE_ITEM); this.ids = ids; } }
    @Getter @Setter public static class OutpatientBillRefundCompletedEvent extends DomainEvent { private List<Long> ids; public OutpatientBillRefundCompletedEvent() { super(); } public OutpatientBillRefundCompletedEvent(Long id, List<Long> ids) { super(id, AggregateTypeConstants.CHARGE_ITEM); this.ids = ids; } }
    @Getter @Setter public static class InpatientChargeItemsSettledEvent extends DomainEvent { private List<Long> ids; public InpatientChargeItemsSettledEvent() { super(); } public InpatientChargeItemsSettledEvent(Long id, List<Long> ids) { super(id, AggregateTypeConstants.CHARGE_ITEM); this.ids = ids; } }
    @Getter @Setter public static class InpatientChargeItemsRevertedEvent extends DomainEvent { private List<Long> ids; public InpatientChargeItemsRevertedEvent() { super(); } public InpatientChargeItemsRevertedEvent(Long id, List<Long> ids) { super(id, AggregateTypeConstants.CHARGE_ITEM); this.ids = ids; } }
    @Getter @Setter public static class InpatientBillRefundRequestedEvent extends DomainEvent { private List<Long> ids; public InpatientBillRefundRequestedEvent() { super(); } public InpatientBillRefundRequestedEvent(Long id, List<Long> ids) { super(id, AggregateTypeConstants.CHARGE_ITEM); this.ids = ids; } }
    @Getter @Setter public static class InpatientBillRefundCompletedEvent extends DomainEvent { private List<Long> ids; public InpatientBillRefundCompletedEvent() { super(); } public InpatientBillRefundCompletedEvent(Long id, List<Long> ids) { super(id, AggregateTypeConstants.CHARGE_ITEM); this.ids = ids; } }

    /**
     * 半退保留链再生收费行已创建事件：aggregateId 为新生成的 {@code adm_charge_item.id}。
     *
     * <p>须携带 {@link #encounterId} 与 {@link #sourceChargeItemId}，供投影侧将新行与就诊及源结算行关联；
     * 发布前由收费明细写侧从已落库行写入本载荷。</p>
     */
    @Getter
    @Setter
    public static class PreservedLineRegeneratedChargeItemCreatedEvent extends DomainEvent {

        /** 关联就诊主键，与 {@code adm_charge_item.encounter_id} 一致。 */
        private Long encounterId;

        /** 源保留行（SETTLED）收费明细主键；新行 {@code refund_id} 与之相等（BR-CHG-015）。 */
        private Long sourceChargeItemId;

        /** 无参构造器（反序列化用）。 */
        public PreservedLineRegeneratedChargeItemCreatedEvent() {
            super();
        }

        /**
         * 构造器：仅设置聚合类型与新生成 ChargeItem 主键。
         *
         * @param newChargeItemId 新生成的收费明细主键
         */
        public PreservedLineRegeneratedChargeItemCreatedEvent(Long newChargeItemId) {
            super(newChargeItemId, AggregateTypeConstants.CHARGE_ITEM);
        }
    }
}
