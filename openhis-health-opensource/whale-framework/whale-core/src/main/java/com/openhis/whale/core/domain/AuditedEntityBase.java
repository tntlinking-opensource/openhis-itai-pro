/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.domain;

import com.openhis.whale.core.infrastructure.auditing.AuditingEntityListener;
import com.openhis.whale.core.infrastructure.id.SnowflakeId;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/**
 * 审计实体基类（无 {@code tenant_id} 列）。
 * <p>用户主数据等通过关联表表达租户关系时使用本基类；业务数据行级租户隔离仍用 {@link BaseAuditedEntity}。</p>
 * <p>实现 {@link Persistable}：{@code repository.save} 走 {@code persist} 时由 {@link SnowflakeId}
 * 在 insert 阶段生成主键；<b>insert 前勿手动 {@code setId}</b>，否则 Hibernate 6 可能报
 * {@code detached entity passed to persist}。更新已加载实体时 {@code isNew()} 为 false，走 {@code merge}。</p>
 * <p>软删读侧：须同时具备 {@link FilterDef} + {@link Filter}，由 {@code SoftDeleteFilterAspect}
 * 在 Repository 调用前 {@code enableFilter("deletedFilter")}；仅有 {@code @Filter} 而无 {@code @FilterDef}
 * 时启用失败会被切面吞掉，导致「软删后重插」读到旧行+新行双份。</p>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@FilterDef(name = "deletedFilter", parameters = @ParamDef(name = "isDeleted", type = boolean.class))
@Filter(name = "deletedFilter", condition = "is_deleted = :isDeleted")
@Getter
@Setter
public abstract class AuditedEntityBase implements Persistable<Long> {

    @Id
    @SnowflakeId
    @Column(name = "id", nullable = false)
    private Long id;

    @Transient
    private boolean newEntity = true;

    @Override
    public boolean isNew() {
        return newEntity || id == null;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }

    /** 克隆/重建实体后 insert 前调用：清空 id 并标记为新行（主键由 {@link SnowflakeId} 生成）。 */
    public void prepareForInsert() {
        this.id = null;
        this.newEntity = true;
    }

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;
}
