# 平台字典同步 · 应用服务详细设计（SDD）

> 编码：**AS-CHS-FND-003-SDD** · v0.1 · 2026-07-11  
> **应用服务编号**：AS-CHS-FND-003

## 1. 边界

| AppService | HTTP | infno |
| --- | --- | --- |
| `InsurancePlatformDictAppService` | `/api/app/insurancePlatformDict` | 1901 |

## 2. 包结构

```text
platformdict/
  service/InsurancePlatformDictAppService.java
  request/  mapper/InsurancePlatformDictAppMapper.java
  invoker/Dict1901PageInvoker.java
  support/Dict1901SyncWorkflow.java
```

包路径：`com.zxplat.plugin.chs.application.foundation.platformdict.*`

## 3. HTTP ↔ IF

| 方法 | IF | 编排 |
| --- | --- | --- |
| `syncFrom1901` | IF-CHS-FND-003-W-01 | `Dict1901SyncWorkflow` |
| `lookupDictLabels` | IF-CHS-FND-003-R-01 | domain 本地字典 |
| `getLastSyncLog` | IF-CHS-FND-003-R-02 | domain sync log |
| `queryPlatformDictPage` | IF-CHS-FND-003-R-03 | 本地分页 |

## 4. 事务

`syncFrom1901` 写路径 REQUIRED；其余只读。
