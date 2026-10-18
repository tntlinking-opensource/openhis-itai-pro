# DM-MAP-001 MapWechatAccount · DomainModel-SDD

> **对应 DM**：`../DomainModel/DM-MAP-001-MapWechatAccount-微信账号.md`  
> **包**：`com.zxplat.plugin.miniapp.domain.wechataccount` · **版本**：v1.1 · **日期**：2026-09-24

## 1. 包结构

`entity` / `repository`

## 4. 字段全集（`map_wechat_account`）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| appId | String | 是 | 小程序 appId |
| openid | String | 是 | 微信 openid |
| unionId | String | 否 | unionId |
| statusEnum | String | 是 | 默认 `active` |
| lastLoginAt | LocalDateTime | 否 | 最近登录 |

## 5. 写方法

无独立 DomainService；创建/刷新由 `MapAuthDomainService.loginOrCreateSession` 编排。

## 2. 仓储白名单

```text
findFirstByTenantIdAndAppIdAndOpenidAndIsDeletedFalse(tenantId, appId, openid)
save(...)
```

## 9. 修订

| v1.1 | 2026-09-24 | 补 §4 字段全集 |
| v1.0 | 2026-09-24 | Mode B 迁入 |
