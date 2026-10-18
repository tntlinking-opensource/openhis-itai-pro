# DM-MAP-003 MapPatientBinding · DomainModel-SDD

> **对应 DM**：`../DomainModel/DM-MAP-003-MapPatientBinding-就诊人绑定.md`  
> **包**：`com.zxplat.plugin.miniapp.domain.patientbinding` · **版本**：v1.1 · **日期**：2026-09-24

## 4. 字段全集（`map_patient_binding`）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| wechatAccountId | Long | 是 | 微信账号 |
| patientId | Long | 是 | 就诊人 |
| bindChannelEnum | String | 是 | 渠道 |
| campusRefType | String | 否 | 校园主体类型 |
| campusRefId | Long | 否 | 校园主体 id |
| campusBizNo | String | 否 | 学号/工号等 |
| isDefault | Boolean | 是 | 默认就诊人 |
| boundAt / unboundAt | LocalDateTime | 是/否 | 生命周期 |

## 5. 写方法

经 AUTH：`bindPatient` / `switchPatient`。

## 9. 修订

| v1.1 | 2026-09-24 | 补 §4 |
| v1.0 | 2026-09-24 | Mode B 迁入 |
