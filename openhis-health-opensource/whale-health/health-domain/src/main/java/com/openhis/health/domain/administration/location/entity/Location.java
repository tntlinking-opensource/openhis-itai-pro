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

package com.openhis.health.domain.administration.location.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationMode;
import com.openhis.health.domain.share.enums.LocationOperational;
import com.openhis.health.domain.share.enums.LocationStatus;
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
 * 位置聚合根实体。
 */
@Getter
@Setter
@Entity
@Table(name = "adm_location")
public class Location extends BaseAuditedEntity {

    /** 名称。 */
    @Column(name = "name", nullable = false)
    private String name;

    /** bus 编号。 */
    @Column(name = "bus_no", nullable = false)
    private String busNo;

    /** status 枚举值。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /** operational 枚举值。 */
    @Column(name = "operational_enum")
    private String operationalEnum;

    /** form 枚举值。 */
    @Column(name = "form_enum", nullable = false)
    private String formEnum;

    /** mode 枚举值。 */
    @Column(name = "mode_enum", nullable = false)
    private String modeEnum;

    /** type JSON 快照。 */
    @Column(name = "type_json")
    private String typeJson;

    /** organization 标识。 */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** displayOrder。 */
    @Column(name = "display_order")
    private Integer displayOrder;

    /** pyStr。 */
    @Column(name = "py_str")
    private String pyStr;

    /** wbStr。 */
    @Column(name = "wb_str")
    private String wbStr;

    /** parent 标识。 */
    @Column(name = "parent_id")
    private Long parentId;

    /**
     * 初始化登记状态（草稿）。
     */
    public void initDraft() {
        this.statusEnum = LocationStatus.DRAFT.getCode();
        this.applyRegisterDefaults();
    }

    /**
     * 初始化登记状态（直接启用，运营默认未使用）。
     */
    public void initActive() {
        this.statusEnum = LocationStatus.ACTIVE.getCode();
        this.applyRegisterDefaults();
    }

    /**
     * 登记时补齐模式、运营状态与类型 JSON 默认值。
     */
    private void applyRegisterDefaults() {
        if (this.modeEnum == null) {
            this.modeEnum = LocationMode.INSTANCE.getCode();
        }
        if (this.operationalEnum == null) {
            this.operationalEnum = LocationOperational.NOT_IN_USE.getCode();
        }
        if (StringUtils.isBlank(this.typeJson)) {
            this.typeJson = "{}";
        }
    }

    /**
     * 断言登记必填字段。
     */
    public void assertRegisterRequiredFields() {
        if (StringUtils.isBlank(this.name)) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_INVALID_ARGUMENT, "位置名称不能为空");
        }
        if (this.formEnum == null) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_INVALID_ARGUMENT, "物理形式不能为空");
        }
        if (this.organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_INVALID_ARGUMENT, "机构标识不能为空");
        }
    }

    /**
     * 断言允许启用（草稿首次启用，或停用后重新启用）。
     */
    public void assertActivatable() {
        if (!LocationStatus.DRAFT.getCode().equals(this.statusEnum)
            && !LocationStatus.INACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_INVALID_STATUS, "仅草稿或停用位置可启用");
        }
    }

    /**
     * 断言允许暂停。
     */
    public void assertSuspendable() {
        if (!LocationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_INVALID_STATUS, "仅启用位置可暂停");
        }
        this.assertNotOccupiedBed();
    }

    /**
     * 断言允许恢复。
     */
    public void assertResumable() {
        if (!LocationStatus.SUSPENDED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_INVALID_STATUS, "仅暂停位置可恢复");
        }
    }

    /**
     * 断言允许停用。
     */
    public void assertDeactivatable() {
        if (!LocationStatus.ACTIVE.getCode().equals(this.statusEnum)
            && !LocationStatus.SUSPENDED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_INVALID_STATUS, "仅启用/暂停位置可停用");
        }
        // 床位运营占用校验仅适用于床位形态；库房/药房等扁平节点无此约束。
        if (LocationForm.BED.getCode().equals(this.formEnum)) {
            this.assertNotOccupiedBed();
        }
    }

    /**
     * 断言允许废弃。
     */
    public void assertAbolishable() {
        if (!LocationStatus.DRAFT.getCode().equals(this.statusEnum)
            && !LocationStatus.INACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_INVALID_STATUS, "仅草稿/停用位置可废弃");
        }
    }

    /**
     * 断言库房/药房等扁平库存节点允许废弃（启用/停用/草稿均可；暂停态需先恢复或停用）。
     */
    public void assertAbolishableAsFlatInventoryNode() {
        if (LocationStatus.SUSPENDED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_INVALID_STATUS, "暂停中的位置不可删除，请先恢复或停用");
        }
    }

    /**
     * 断言可分配患者。
     */
    public void assertPatientAssignable() {
        if (LocationMode.KIND.getCode().equals(this.modeEnum)) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_RULE_CONFLICT, "模板位置不可分配患者");
        }
        if (!LocationStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_INVALID_STATUS, "仅启用位置可进行床位运营");
        }
    }

    /**
     * 断言当前位置为床位形态（formEnum = BED），用于床位运营动作的前置校验。
     *
     * @throws BusinessException 位置非床位
     */
    public void assertIsBed() {
        if (!LocationForm.BED.getCode().equals(this.formEnum)) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_RULE_CONFLICT, "仅床位可执行床位运营动作");
        }
    }

    /**
     * 迁移为启用状态。
     */
    public void activate() {
        this.statusEnum = LocationStatus.ACTIVE.getCode();
    }

    /**
     * 迁移为暂停状态。
     */
    public void suspend() {
        this.statusEnum = LocationStatus.SUSPENDED.getCode();
    }

    /**
     * 迁移为停用状态。
     */
    public void deactivate() {
        this.statusEnum = LocationStatus.INACTIVE.getCode();
    }

    /**
     * 迁移为占用状态。
     */
    public void occupy() {
        this.operationalEnum = LocationOperational.IN_USE.getCode();
    }

    /**
     * 迁移为空闲状态。
     */
    public void release() {
        this.operationalEnum = LocationOperational.NOT_IN_USE.getCode();
    }

    /**
     * 标记逻辑删除。
     *
     * @param operatorId 操作人
     */
    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }

    /**
     * 断言床位非占用。
     */
    private void assertNotOccupiedBed() {
        if (LocationOperational.IN_USE.getCode().equals(this.operationalEnum)) {
            throw new BusinessException(ErrorCodeConstants.LOCATION_RULE_CONFLICT, "占用中的床位不可执行该操作");
        }
    }
}
