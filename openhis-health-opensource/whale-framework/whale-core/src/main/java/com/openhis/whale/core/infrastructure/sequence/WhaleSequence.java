/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.sequence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 业务序号实体，按 key 记录当前序号值。
 *
 * @author Whale Framework
 */
@Entity
@Table(name = "whale_sequence")
@Getter
@Setter
public class WhaleSequence {

    /**
     * 序号键（如 ORDER、PATIENT，或 ORDER:2025-01-30 表示按天）
     */
    @Id
    @Column(name = "seq_key", nullable = false, length = 255)
    private String seqKey;

    /**
     * 当前序号值
     */
    @Column(name = "current_value", nullable = false)
    private Long currentValue = 0L;

    /**
     * 最后更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
