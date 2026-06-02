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

package com.openhis.health.domain.financial.contract.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.ContractStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;

/**
 * 费用性质聚合根。
 */
@Getter
@Setter
@Entity
@Table(name = "fin_contract")
public class Contract extends BaseAuditedEntity {
    @Column(name = "bus_no", nullable = false)
    private String busNo;
    @Column(name = "name", nullable = false)
    private String name;
    @Column(name = "category_enum", nullable = false)
    private String categoryEnum;
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;
    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag;
    @Column(name = "org_id")
    private Long orgId;
    @Column(name = "yb_flag")
    private Boolean ybFlag;
    @Column(name = "display_order")
    private Integer displayOrder;
    @Column(name = "adm_vs")
    private String admVs;
    @Column(name = "py_str")
    private String pyStr;
    @Column(name = "wb_str")
    private String wbStr;
    @Column(name = "client_id")
    private String clientId;
    @Column(name = "client_secret")
    private String clientSecret;
    @Column(name = "username")
    private String username;
    @Column(name = "password")
    private String password;
    @Column(name = "cli_prv_key")
    private String cliPrvKey;
    @Column(name = "cli_pub_key")
    private String cliPubKey;
    @Column(name = "server_pub_key")
    private String serverPubKey;
    @Column(name = "scope")
    private String scope;
    @Column(name = "grant_type")
    private String grantType;
    @Column(name = "fixmedins_code")
    private String fixmedinsCode;
    @Column(name = "fixmedins_name")
    private String fixmedinsName;

    /**
      * 断言业务前置条件满足。
     */
    public void assertBusNoNotBlank() {
        if (StringUtils.isBlank(this.busNo)) {
            throw new BusinessException(ErrorCodeConstants.CONTRACT_INVALID_ARGUMENT, "费用性质编码不能为空");
        }
    }

    /**
      * 断言业务前置条件满足。
     */
    public void assertDisplayOrderNonNegative() {
        if (this.displayOrder != null && this.displayOrder < 0) {
            throw new BusinessException(ErrorCodeConstants.CONTRACT_INVALID_ARGUMENT, "显示顺序必须为非负整数");
        }
    }

    /**
      * 初始化领域状态。
     */
    public void initActive() {
        this.statusEnum = ContractStatus.ACTIVE.getCode();
        this.activeFlag = Boolean.TRUE;
    }

    /**
      * 刷新衍生字段值。
     */
    public void refreshSearchCodeFromName() {
        this.pyStr = this.name;
        this.wbStr = this.name;
    }

    /**
      * 将状态切换为启用。
     */
    public void activate() {
        this.statusEnum = ContractStatus.ACTIVE.getCode();
        this.activeFlag = Boolean.TRUE;
    }

    /**
      * 将状态切换为停用。
     */
    public void deactivate() {
        this.statusEnum = ContractStatus.INACTIVE.getCode();
        this.activeFlag = Boolean.FALSE;
    }

    /**
     * 逻辑删除合同并记录操作人。
     *
     * @param operatorId 操作人主键
     */
    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }
}

