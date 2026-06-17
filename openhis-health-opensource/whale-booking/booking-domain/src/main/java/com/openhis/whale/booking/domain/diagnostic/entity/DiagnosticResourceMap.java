package com.openhis.whale.booking.domain.diagnostic.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "bkg_diagnostic_resource_map")
public class DiagnosticResourceMap extends BaseAuditedEntity {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "activity_definition_id")
    private Long activityDefinitionId;

    @Column(name = "service_category_code")
    private String serviceCategoryCode;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "resource_name")
    private String resourceName;

    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag = Boolean.TRUE;
}
