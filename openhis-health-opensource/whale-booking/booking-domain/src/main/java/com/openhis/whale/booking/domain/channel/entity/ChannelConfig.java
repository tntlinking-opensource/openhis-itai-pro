package com.openhis.whale.booking.domain.channel.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 渠道配置（bkg_channel_config）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_channel_config")
public class ChannelConfig extends BaseAuditedEntity {

    /** 机构标识；空=租户默认。 */
    @Column(name = "organization_id")
    private Long organizationId;

    /** 渠道编码：WINDOW / APP / WECHAT 等。 */
    @Column(name = "channel_code", nullable = false)
    private String channelCode;

    /** 渠道名称。 */
    @Column(name = "channel_name", nullable = false)
    private String channelName;

    /** 是否启用。 */
    @Column(name = "enabled_flag", nullable = false)
    private Boolean enabledFlag = Boolean.TRUE;

    /** 号源配额百分比（0-100）。 */
    @Column(name = "quota_percent", nullable = false)
    private Integer quotaPercent = 100;

    /** 通知模板编码。 */
    @Column(name = "notify_template_code")
    private String notifyTemplateCode;
}
