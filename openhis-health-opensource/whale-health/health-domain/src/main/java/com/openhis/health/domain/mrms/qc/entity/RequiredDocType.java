/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.mrms.qc.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 必写文书类型配置（{@code mrms_required_doc_type}），文书完整性质控用。
 *
 * <p>{@code tenant_id} 语义同 {@link com.openhis.health.domain.share.constants.GeneralSharedConstants#PLATFORM_TENANT_ID} 种子与租户覆盖。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_required_doc_type")
public class RequiredDocType extends BaseAuditedEntity {

    /** EMR 文书类型 code，与质控规则 document_type 对齐。 */
    @Column(name = "document_type_code", nullable = false, length = 64)
    private String documentTypeCode;

    /** 适用就诊类别 code；空表示全部类别生效。 */
    @Column(name = "encounter_class_code", length = 16)
    private String encounterClassCode;

    /** 是否必写（true 时缺失则产生文书完整性缺陷）。 */
    @Column(name = "required", nullable = false)
    private Boolean required;
}
