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

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "channel_code", nullable = false)
    private String channelCode;

    @Column(name = "channel_name", nullable = false)
    private String channelName;

    @Column(name = "enabled_flag", nullable = false)
    private Boolean enabledFlag = Boolean.TRUE;

    @Column(name = "quota_percent", nullable = false)
    private Integer quotaPercent = 100;

    @Column(name = "notify_template_code")
    private String notifyTemplateCode;
}
