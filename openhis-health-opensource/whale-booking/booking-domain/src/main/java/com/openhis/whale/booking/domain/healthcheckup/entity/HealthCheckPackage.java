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

    /** 机构标识。 */
    @Column(name = "organization_id")
    private Long organizationId;

    /** 套餐编码。 */
    @Column(name = "package_code", nullable = false)
    private String packageCode;

    /** 套餐名称。 */
    @Column(name = "package_name", nullable = false)
    private String packageName;

    /** 适用性别：M/F/ALL。 */
    @Column(name = "applicable_gender")
    private String applicableGender;

    /** 适用年龄下限。 */
    @Column(name = "applicable_age_min")
    private Integer applicableAgeMin;

    /** 适用年龄上限。 */
    @Column(name = "applicable_age_max")
    private Integer applicableAgeMax;

    /** 参考金额。 */
    @Column(name = "estimated_amount")
    private BigDecimal estimatedAmount;

    /** 是否启用。 */
    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag = Boolean.TRUE;
}
