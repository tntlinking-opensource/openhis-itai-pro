/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.chargeitemdefinition;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;


/**
 * 收费项目定义（ChargeItemDefinition）领域事件集合，供跨模块订阅价目行变更、批量更新与调价记录。
 */
public final class ChargeItemDefinitionDomainEvents {
    /** 工具类，禁止实例化。 */
    private ChargeItemDefinitionDomainEvents() {
    }

    /** 价目行已新增事件。 */
    @Getter
    @Setter
    public static class PriceLineAddedEvent extends DomainEvent {
        public PriceLineAddedEvent() { super(); }
        public PriceLineAddedEvent(Long id) { super(id, AggregateTypeConstants.CHARGE_ITEM_DEFINITION); }
    }

    /** 价目行已更新事件。 */
    @Getter
    @Setter
    public static class PriceLineUpdatedEvent extends DomainEvent {
        public PriceLineUpdatedEvent() { super(); }
        public PriceLineUpdatedEvent(Long id) { super(id, AggregateTypeConstants.CHARGE_ITEM_DEFINITION); }
    }

    /** 价目行已移除事件。 */
    @Getter
    @Setter
    public static class PriceLineRemovedEvent extends DomainEvent {
        public PriceLineRemovedEvent() { super(); }
        public PriceLineRemovedEvent(Long id) { super(id, AggregateTypeConstants.CHARGE_ITEM_DEFINITION); }
    }

    /** 价目行批量新增事件。 */
    @Getter
    @Setter
    public static class PriceBatchAddedEvent extends DomainEvent {
        /** 本批新增价目行条数。 */
        private Integer size;
        public PriceBatchAddedEvent() { super(); }
        /**
         * @param id   收费项目定义聚合根标识
         * @param size 本批新增价目行条数
         */
        public PriceBatchAddedEvent(Long id, Integer size) {
            super(id, AggregateTypeConstants.CHARGE_ITEM_DEFINITION);
            this.size = size;
        }
    }

    /** 收费项目定义信息批量更新事件（按业务实例定位）。 */
    @Getter
    @Setter
    public static class ChargeItemDefinitionInfoBatchUpdatedEvent extends DomainEvent {
        /** 业务实例主键（与 {@code instance_table} 联合唯一定位收费项目定义）。 */
        private Long instanceId;
        /** 业务实例来源表名（与 adm_charge_item_definition.instance_table 一致，用于读模型唯一定位）。 */
        private String instanceTable;
        public ChargeItemDefinitionInfoBatchUpdatedEvent() { super(); }
        /**
         * @param id         收费项目定义聚合根标识
         * @param instanceId 业务实例主键
         */
        public ChargeItemDefinitionInfoBatchUpdatedEvent(Long id, Long instanceId) {
            super(id, AggregateTypeConstants.CHARGE_ITEM_DEFINITION);
            this.instanceId = instanceId;
        }

        /**
         * @param id            收费项目定义聚合根标识
         * @param instanceId    业务实例主键
         * @param instanceTable 业务实例来源表名
         */
        public ChargeItemDefinitionInfoBatchUpdatedEvent(Long id, Long instanceId, String instanceTable) {
            super(id, AggregateTypeConstants.CHARGE_ITEM_DEFINITION);
            this.instanceId = instanceId;
            this.instanceTable = instanceTable;
        }
    }

    /** 按业务实例批量软删收费项目定义事件。 */
    @Getter
    @Setter
    public static class ChargeItemDefinitionRemovedByInstanceEvent extends DomainEvent {
        /** 业务实例主键（与 {@code instance_table} 联合唯一定位收费项目定义）。 */
        private Long instanceId;
        /** 本次按实例批量软删的收费项目定义主键列表。 */
        private List<Long> removedIds;
        /** 业务实例来源表名；与 {@link ChargeItemDefinitionInfoBatchUpdatedEvent#instanceTable} 语义一致。 */
        private String instanceTable;
        public ChargeItemDefinitionRemovedByInstanceEvent() { super(); }
        /**
         * @param id         收费项目定义聚合根标识
         * @param instanceId 业务实例主键
         * @param removedIds 本次软删的主键列表
         */
        public ChargeItemDefinitionRemovedByInstanceEvent(Long id, Long instanceId, List<Long> removedIds) {
            super(id, AggregateTypeConstants.CHARGE_ITEM_DEFINITION);
            this.instanceId = instanceId;
            this.removedIds = removedIds;
        }

        /**
         * @param id            收费项目定义聚合根标识
         * @param instanceId    业务实例主键
         * @param removedIds    本次软删的主键列表
         * @param instanceTable 业务实例来源表名
         */
        public ChargeItemDefinitionRemovedByInstanceEvent(Long id, Long instanceId, List<Long> removedIds, String instanceTable) {
            super(id, AggregateTypeConstants.CHARGE_ITEM_DEFINITION);
            this.instanceId = instanceId;
            this.removedIds = removedIds;
            this.instanceTable = instanceTable;
        }
    }

    /** 调价记录已写入事件。 */
    @Getter
    @Setter
    public static class PriceAdjustmentRecordedEvent extends DomainEvent {
        /** 调价前单价（元）。 */
        private BigDecimal oldPrice;
        /** 调价后单价（元）。 */
        private BigDecimal newPrice;
        public PriceAdjustmentRecordedEvent() { super(); }
        /**
         * @param id       收费项目定义聚合根标识
         * @param oldPrice 调价前单价（元）
         * @param newPrice 调价后单价（元）
         */
        public PriceAdjustmentRecordedEvent(Long id, BigDecimal oldPrice, BigDecimal newPrice) {
            super(id, AggregateTypeConstants.CHARGE_ITEM_DEFINITION);
            this.oldPrice = oldPrice;
            this.newPrice = newPrice;
        }
    }
}
