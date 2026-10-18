/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.supplyrequest.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 创建库存期初执行单命令（AS-BAS-INV-001 · GAP-OPN-001 · {@code createOpening}）。
 *
 * <p>对齐损益/盘点免申请执行单据形态：仅写 {@code purpose_*}；新建可空明细（空草稿）；
 * 首行 {@code busNo} 非空表示同号全量修订（软删+重插）。</p>
 *
 * <p>空草稿时须在命令顶层携带 {@link #typeEnum}/{@link #purposeTypeEnum}/{@link #purposeLocationId}
 *（因 {@link BatchCommand#resolveItems()} 拒绝双空）。</p>
 */
@Getter
@Setter
public class CreateOpeningCommand extends BatchCommand<CreateOpeningCommand.CreateOpeningItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 空草稿时的单据类型持久化码（三类 OPENING 之一）。
     *
     * <p>有明细时以首行 {@code typeEnum} 为准；空明细新建时本字段必填。</p>
     */
    private String typeEnum;

    /**
     * 空草稿时的目的库房位置类型持久化码。
     */
    private String purposeTypeEnum;

    /**
     * 空草稿时的目的库房位置主键。
     */
    private Long purposeLocationId;

    /**
     * 空草稿时的业务发生时间（可选）。
     */
    private LocalDateTime occurrenceTime;

    /**
     * 空草稿时的申请人主键（可选）。
     */
    private Long applicantId;

    /**
     * 空草稿时的经手人主键（可选）。
     */
    private Long practitionerId;

    /**
     * 解析明细；允许空列表（空草稿），与 {@link BatchCommand#resolveItems()} 不同。
     *
     * @return 非 null 列表（可 empty）
     */
    public List<CreateOpeningItemCommand> resolveOpeningItemsAllowEmpty() {
        List<CreateOpeningItemCommand> batch = this.getItems();
        if (batch != null && !batch.isEmpty()) {
            return batch;
        }
        CreateOpeningItemCommand single = this.getItem();
        if (single != null) {
            List<CreateOpeningItemCommand> normalized = new ArrayList<>();
            normalized.add(single);
            return normalized;
        }
        return new ArrayList<>();
    }

    /**
     * 期初单行（单端点 purpose：目标库房）。
     */
    @Getter
    @Setter
    public static class CreateOpeningItemCommand {
        /** 业务单号；修订时首行必填既有单号，新建时留空由领域生成。 */
        private String busNo;
        /** 供应类型持久化码（三类 OPENING）。 */
        private String typeEnum;
        /** 物品来源表。 */
        private String itemTable;
        /** 物品定义主键。 */
        private Long itemId;
        /** 期初数量（拆零，须 &gt; 0）。 */
        private BigDecimal itemQuantity;
        /** 计量单位编码。 */
        private String unitCode;
        /** 供应类别持久化码。 */
        private String categoryEnum;
        /** 目标库房位置类型持久化码（单端点真源）。 */
        private String purposeTypeEnum;
        /** 目标库房位置主键（单端点真源 → {@code purpose_location_id}）。 */
        private Long purposeLocationId;
        /** 批号。 */
        private String lotNumber;
        /** 生产日期（可选）。 */
        private LocalDateTime startTime;
        /** 效期（有明细时业务层必填；领域落 {@code end_time}）。 */
        private LocalDateTime endTime;
        /** 业务发生时间。 */
        private LocalDateTime occurrenceTime;
        /** 申请人主键。 */
        private Long applicantId;
        /** 经手人主键。 */
        private Long practitionerId;
        /** 申请时间。 */
        private LocalDateTime applyTime;
        /** 原因说明。 */
        private String reason;
        /** 行备注（库列 {@code remake}）。 */
        private String remake;
        /** 进价（药库期初应用层硬必填）。 */
        private BigDecimal price;
        /** 售价（可选）。 */
        private BigDecimal salePrice;
        /** 行金额（可选）。 */
        private BigDecimal totalPrice;
        /** 供应商主键（可选）。 */
        private Long supplierId;
        /** 追溯码逗号串（草稿暂存）。 */
        private String traceNo;
    }
}
