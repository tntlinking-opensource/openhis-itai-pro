package com.openhis.whale.booking.domain.healthcheckup.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 体检套餐明细（bkg_health_check_package_item）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_health_check_package_item")
public class HealthCheckPackageItem extends BaseAuditedEntity {

    @Column(name = "package_id", nullable = false)
    private Long packageId;

    @Column(name = "activity_definition_id")
    private Long activityDefinitionId;

    @Column(name = "item_name")
    private String itemName;

    @Column(name = "required_flag", nullable = false)
    private Boolean requiredFlag = Boolean.TRUE;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
