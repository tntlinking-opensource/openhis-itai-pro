/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.administration.practitioner.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PractitionerStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 医务人员聚合根实体。
 */
@Getter
@Setter
@Entity
@Table(name = "adm_practitioner")
public class Practitioner extends BaseAuditedEntity {

    @Column(name = "name", nullable = false)
    private String name;
    @Column(name = "name_json")
    private String nameJson;
    @Column(name = "gender_enum", nullable = false)
    private String genderEnum;
    @Column(name = "birth_date")
    private LocalDate birthDate;
    @Column(name = "deceased_date")
    private LocalDateTime deceasedDate;
    @Column(name = "phone")
    private String phone;
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;
    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag;
    @Column(name = "bus_no")
    private String busNo;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "org_id")
    private Long orgId;
    @Column(name = "address_province")
    private String addressProvince;
    @Column(name = "address_city")
    private String addressCity;
    @Column(name = "address_district")
    private String addressDistrict;
    @Column(name = "address_street")
    private String addressStreet;
    @Column(name = "address_json")
    private String addressJson;
    @Column(name = "yb_no")
    private String ybNo;
    @Column(name = "dr_profttl_code")
    private Integer drProfttlCode;
    @Column(name = "phar_prac_cert_no")
    private String pharPracCertNo;
    @Column(name = "prsc_dr_cert_code")
    private String prscDrCertCode;
    @Column(name = "kpd_code")
    private String kpdCode;
    @Column(name = "pos_no")
    private String posNo;
    /** 医生签名图片 Base64（可含 data:image/*;base64, 前缀）；库列为 PostgreSQL TEXT */
    @Column(name = "signature", columnDefinition = "TEXT")
    private String signature;
    @Column(name = "py_str")
    private String pyStr;
    @Column(name = "wb_str")
    private String wbStr;

    /**
      * 初始化领域状态。
     */
    public void initAsActive() {
        this.statusEnum = PractitionerStatus.ACTIVE.getCode();
        this.activeFlag = Boolean.TRUE;
    }

    /**
      * 断言业务前置条件满足。
     */
    public void assertCreateAllowed() {
        if (this.userId == null) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "用户标识不能为空");
        }
        if (StringUtils.isBlank(this.name)) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "人员姓名不能为空");
        }
    }

    /**
      * 断言业务前置条件满足。
     */
    public void assertEditable() {
        if (!PractitionerStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_STATUS, "仅启用人员可编辑");
        }
    }

    /**
      * 断言业务前置条件满足。
     */
    public void assertDeactivatable() {
        if (!PractitionerStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_STATUS, "仅启用人员可停用");
        }
    }

    /**
      * 断言业务前置条件满足。
     */
    public void assertActivatable() {
        if (!PractitionerStatus.INACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_STATUS, "仅停用人员可启用");
        }
    }

    /**
      * 断言业务前置条件满足。
     */
    public void assertRetirable() {
        if (!PractitionerStatus.ACTIVE.getCode().equals(this.statusEnum)
            && !PractitionerStatus.INACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_STATUS, "仅启用/停用人员可废弃");
        }
    }

    /**
      * 断言业务前置条件满足。
     */
    public void assertSwitchOrgAllowed() {
        if (!PractitionerStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_INVALID_STATUS, "仅启用人员可切换科室");
        }
    }

    /**
      * 处理transitToInactive逻辑。
     */
    public void transitToInactive() {
        this.statusEnum = PractitionerStatus.INACTIVE.getCode();
        this.activeFlag = Boolean.FALSE;
    }

    /**
      * 处理transitToActive逻辑。
     */
    public void transitToActive() {
        this.statusEnum = PractitionerStatus.ACTIVE.getCode();
        this.activeFlag = Boolean.TRUE;
    }

    /**
     * 退休/注销医务人员：状态迁至已删除并软删，记录操作人。
     *
     * @param operatorId 操作人主键
     */
    public void transitToDeleted(Long operatorId) {
        this.statusEnum = PractitionerStatus.DELETED.getCode();
        this.activeFlag = Boolean.FALSE;
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }
}

