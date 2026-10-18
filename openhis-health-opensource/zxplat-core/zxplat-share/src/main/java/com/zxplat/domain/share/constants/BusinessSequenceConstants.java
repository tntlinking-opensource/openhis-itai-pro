/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 与 {@link com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService} 配合的序号位数等数值常量。
 *
 * <p>口径真源：docs {@code 代码生成通用规范} {@code full-standards.md} {@code B.10.9}。</p>
 */
public interface BusinessSequenceConstants {

    /** 业务流水去前缀后的当日序号位数（{@code getSeqByDay} 默认后缀）。 */
    int BUS_NO_SEQ_LENGTH = 4;

    /** 扁平主数据：2 位前缀 + 本字段 = 总长 10 位（如 {@code PA00000001}）。 */
    int MASTER_DATA_2_PREFIX_SEQ_LENGTH = 8;

    /** 扁平主数据：3 位前缀 + 本字段 = 总长 10 位（如 {@code SPD0000001}）。 */
    int MASTER_DATA_3_PREFIX_SEQ_LENGTH = 7;

    /**
     * 级联单段序号位数（仅 LO/OR 等 {@code 父.子} 场景；段内允许短于 10 位）。
     */
    int CASCADE_BUS_NO_SEGMENT_SEQ_LENGTH = 4;

    /**
     * 诊疗/耗材目录 busNo 序号位数：前缀 2 位（AD/DD）+ 本字段 = 共 10 位。
     */
    int CATALOG_DEFINITION_BUS_NO_SEQ_LENGTH = MASTER_DATA_2_PREFIX_SEQ_LENGTH;

    /**
     * 库存明细备份批次单号序号位数（DM-DOC-001 · {@code ABU}+{@code yyyyMMdd}+本字段）。
     */
    int INVENTORY_DETAIL_SNAPSHOT_BUS_NO_SEQ_LENGTH = 2;
}
