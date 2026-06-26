package com.openhis.whale.report.domain.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.whale.core.domain.IMultiTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * C 类报表列定制 override。表 {@code rpt_column_override}。
 */
@Getter
@Setter
@Entity
@Table(name = "rpt_column_override")
public class ReportColumnOverride extends BaseAuditedEntity implements IMultiTenant {

    /** 报表编码，与 {@code rpt_definition.report_code} 对应。 */
    @Column(name = "report_code", nullable = false, length = 64)
    private String reportCode;

    /** 用户主键；与 organizationId 互斥（用户级 override）。 */
    @Column(name = "user_id")
    private Long userId;

    /** 机构主键；userId 为空时表示机构级 override。 */
    @Column(name = "organization_id")
    private Long organizationId;

    /** 列配置 JSON 数组，元素为 {@link com.openhis.whale.report.domain.support.ReportColumnOverrideItem}。 */
    @Column(name = "columns_json", nullable = false, columnDefinition = "TEXT")
    private String columnsJson;
}
