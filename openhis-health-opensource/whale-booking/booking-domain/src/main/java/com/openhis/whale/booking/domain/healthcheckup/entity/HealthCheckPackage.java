package com.openhis.whale.booking.domain.healthcheckup.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 体检套餐（bkg_health_check_package）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_health_check_package")
public class HealthCheckPackage extends BaseAuditedEntity {

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "package_code", nullable = false)
    private String packageCode;

    @Column(name = "package_name", nullable = false)
    private String packageName;

    @Column(name = "applicable_gender")
    private String applicableGender;

    @Column(name = "applicable_age_min")
    private Integer applicableAgeMin;

    @Column(name = "applicable_age_max")
    private Integer applicableAgeMax;

    @Column(name = "estimated_amount")
    private BigDecimal estimatedAmount;

    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag = Boolean.TRUE;
}
