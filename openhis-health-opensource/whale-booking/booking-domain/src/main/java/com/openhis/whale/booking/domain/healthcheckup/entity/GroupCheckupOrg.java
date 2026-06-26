package com.openhis.whale.booking.domain.healthcheckup.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 团检单位（bkg_group_checkup_org）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_group_checkup_org")
public class GroupCheckupOrg extends BaseAuditedEntity {

    /** 单位编码。 */
    @Column(name = "org_code", nullable = false)
    private String orgCode;

    /** 单位名称。 */
    @Column(name = "org_name", nullable = false)
    private String orgName;

    /** 联系人。 */
    @Column(name = "contact_name")
    private String contactName;

    /** 联系电话。 */
    @Column(name = "contact_phone")
    private String contactPhone;

    /** 结算方式。 */
    @Column(name = "settlement_mode")
    private String settlementMode;

    /** 合同年度。 */
    @Column(name = "contract_year")
    private String contractYear;
}
