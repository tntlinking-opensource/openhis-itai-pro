/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.account.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.zxplat.framework.core.application.util.StringUtils;

import java.math.BigDecimal;

/**
 * 就诊账户聚合根：与单次就诊关联的分账账户（如自费/医保等）。
 */
@Getter
@Setter
@Entity
@Table(name = "adm_account")
public class Account extends BaseAuditedEntity {
    /** patient 标识。 */
    @Column(name = "patient_id", nullable = false)
    private Long patientId;
    /** encounter 标识。 */
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;
    /** type 代码。 */
    @Column(name = "type_code", nullable = false)
    private String typeCode;
    /** 业务流水号（新建必填）。 */
    @Column(name = "bus_no", nullable = false)
    private String busNo;
    /** 名称。 */
    @Column(name = "name")
    private String name;
    /** balance。 */
    @Column(name = "balance")
    private BigDecimal balance;
    /** 结账状态（{@link com.zxplat.domain.share.enums.AccountBillingStatus#getCode()}）。 */
    @Column(name = "billing_status_enum")
    private String billingStatusEnum;
    /** contract 编号。 */
    @Column(name = "contract_no")
    private String contractNo;
    /** ybArea 编号。 */
    @Column(name = "yb_area_no")
    private String ybAreaNo;
    /** encounter 标志位。 */
    @Column(name = "encounter_flag")
    private Boolean encounterFlag;
    /** insutype。 */
    @Column(name = "insutype")
    private String insutype;
    /** default 标志位。 */
    @Column(name = "default_flag")
    private Boolean defaultFlag;
    /** 账户主状态（有效/无效，{@link com.zxplat.domain.share.enums.PublicationStatus#getCode()}）。 */
    @Column(name = "status_enum")
    private String statusEnum;

    /**
     * 断言新建行在落库前已满足 {@code adm_account} 与 JPA 映射上的非空列（患者、就诊、类型、业务号）。
     *
     * <p>由 {@link com.zxplat.domain.administration.account.service.AccountDomainService} 在装配完字段后调用，
     * 将「必填未填」在领域层阻断，避免依赖 JDBC 非空约束报错。</p>
     *
     * @throws BusinessException 任一必填标识或业务号缺失
     */
    public void assertPersistableNewRow() {
        if (this.patientId == null) {
            throw new BusinessException(ErrorCodeConstants.ACCOUNT_INVALID_ARGUMENT, "开立账户时患者标识不能为空");
        }
        if (this.encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.ACCOUNT_INVALID_ARGUMENT, "开立账户时就诊标识不能为空");
        }
        if (StringUtils.isEmpty(this.typeCode)) {
            throw new BusinessException(ErrorCodeConstants.ACCOUNT_INVALID_ARGUMENT, "开立账户时账户类型编码不能为空");
        }
        if (StringUtils.isEmpty(this.busNo)) {
            throw new BusinessException(ErrorCodeConstants.ACCOUNT_INVALID_ARGUMENT, "开立账户时业务流水号不能为空");
        }
    }
}
