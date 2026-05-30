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

package com.openhis.health.domain.share.events.administration.chargeitemdefinition;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

public final class ChargeItemDefinitionDomainEvents {
    /**
     * 处理逻辑。
     * @return 返回结果
     */
    private ChargeItemDefinitionDomainEvents() {
    }

    @Getter
    @Setter
    public static class PriceLineAddedEvent extends DomainEvent {
        public PriceLineAddedEvent() { super(); }
        public PriceLineAddedEvent(Long id) { super(id, AggregateTypeConstants.CHARGE_ITEM_DEFINITION); }
    }

    @Getter
    @Setter
    public static class PriceLineUpdatedEvent extends DomainEvent {
        public PriceLineUpdatedEvent() { super(); }
        public PriceLineUpdatedEvent(Long id) { super(id, AggregateTypeConstants.CHARGE_ITEM_DEFINITION); }
    }

    @Getter
    @Setter
    public static class PriceLineRemovedEvent extends DomainEvent {
        public PriceLineRemovedEvent() { super(); }
        public PriceLineRemovedEvent(Long id) { super(id, AggregateTypeConstants.CHARGE_ITEM_DEFINITION); }
    }

    @Getter
    @Setter
    public static class PriceBatchAddedEvent extends DomainEvent {
        private Integer size;
        public PriceBatchAddedEvent() { super(); }
        /**
         * 处理Long id, Integer size逻辑。
         * @return 返回结果
         */
        public PriceBatchAddedEvent(Long id, Integer size) {
            super(id, AggregateTypeConstants.CHARGE_ITEM_DEFINITION);
            this.size = size;
        }
    }

    @Getter
    @Setter
    public static class ChargeItemDefinitionInfoBatchUpdatedEvent extends DomainEvent {
        private Long instanceId;
        /** 业务实例来源表名（与 adm_charge_item_definition.instance_table 一致，用于读模型唯一定位）。 */
        private String instanceTable;
        public ChargeItemDefinitionInfoBatchUpdatedEvent() { super(); }
        /**
         * 处理Long id, Long instanceId逻辑。
         * @return 返回结果
         */
        public ChargeItemDefinitionInfoBatchUpdatedEvent(Long id, Long instanceId) {
            super(id, AggregateTypeConstants.CHARGE_ITEM_DEFINITION);
            this.instanceId = instanceId;
        }

        public ChargeItemDefinitionInfoBatchUpdatedEvent(Long id, Long instanceId, String instanceTable) {
            super(id, AggregateTypeConstants.CHARGE_ITEM_DEFINITION);
            this.instanceId = instanceId;
            this.instanceTable = instanceTable;
        }
    }

    @Getter
    @Setter
    public static class ChargeItemDefinitionRemovedByInstanceEvent extends DomainEvent {
        private Long instanceId;
        private List<Long> removedIds;
        /** 业务实例来源表名；与 {@link ChargeItemDefinitionInfoBatchUpdatedEvent#instanceTable} 语义一致。 */
        private String instanceTable;
        public ChargeItemDefinitionRemovedByInstanceEvent() { super(); }
        /**
         * 处理Long id, Long instanceId, List<Long> removedIds逻辑。
         * @return 返回结果
         */
        public ChargeItemDefinitionRemovedByInstanceEvent(Long id, Long instanceId, List<Long> removedIds) {
            super(id, AggregateTypeConstants.CHARGE_ITEM_DEFINITION);
            this.instanceId = instanceId;
            this.removedIds = removedIds;
        }

        public ChargeItemDefinitionRemovedByInstanceEvent(Long id, Long instanceId, List<Long> removedIds, String instanceTable) {
            super(id, AggregateTypeConstants.CHARGE_ITEM_DEFINITION);
            this.instanceId = instanceId;
            this.removedIds = removedIds;
            this.instanceTable = instanceTable;
        }
    }

    @Getter
    @Setter
    public static class PriceAdjustmentRecordedEvent extends DomainEvent {
        /** 调价前单价（元）。 */
        private BigDecimal oldPrice;
        /** 调价后单价（元）。 */
        private BigDecimal newPrice;
        public PriceAdjustmentRecordedEvent() { super(); }
        /**
         * 处理Long id, BigDecimal oldPrice, BigDecimal newPrice逻辑。
         * @return 返回结果
         */
        public PriceAdjustmentRecordedEvent(Long id, BigDecimal oldPrice, BigDecimal newPrice) {
            super(id, AggregateTypeConstants.CHARGE_ITEM_DEFINITION);
            this.oldPrice = oldPrice;
            this.newPrice = newPrice;
        }
    }
}
