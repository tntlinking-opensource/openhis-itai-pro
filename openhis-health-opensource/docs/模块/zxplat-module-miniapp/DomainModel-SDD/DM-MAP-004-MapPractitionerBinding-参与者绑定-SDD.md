# DM-MAP-004 MapPractitionerBinding · DomainModel-SDD

> **对应 DM**：`../DomainModel/DM-MAP-004-MapPractitionerBinding-参与者绑定.md`  
> **包**：`com.zxplat.plugin.miniapp.domain.practitionerbinding` · **版本**：v1.0 · **日期**：2026-09-24

## 1. 包结构

`entity/MapPractitionerBinding` · `repository/MapPractitionerBindingRepository`

## 4. 字段全集

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| wechatAccountId | Long | 是 | 微信账号 |
| practitionerId | Long | 是 | HIS 参与者 |
| bindChannelEnum | String | 是 | 渠道 |
| employeeNo | String | 否 | 工号 |
| isDefault | Boolean | 是 | 默认标记 |
| boundAt | LocalDateTime | 是 | 绑定时刻 |
| unboundAt | LocalDateTime | 否 | 解绑时刻 |

## 5. 写方法

无本聚合 DomainService；写入口见 AUTH SDD §5.1 `bindPractitioner` / `switchPractitioner`。

## 8–9

无缺口。v1.0 Mode B 补齐。
