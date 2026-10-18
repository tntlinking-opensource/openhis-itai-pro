# AS-LIS-CFG-007 LIS系统参数 · 应用服务详细设计（SDD）

> **编码**：AS-LIS-CFG-007-SDD · **版本**：v2.0 · **日期**：2026-06-22
> **对应 AS**：AS-LIS-CFG-007-LIS系统参数.md（v2.0）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat/health-application` |
| **边界** | lis_config KV 参数读写；批量导入 |
| **编排权威** | AS-LIS-CFG-007 §2、§4 |
| **方法权威** | DM-LIS-008 §3 BR + LisConfigDomainService（SDD 推导） |
| **实现阶段口径** | **目标态全量契约**（P1） |

### 术语口径

| 项 | 约定 |
|----|------|
| **事务口径** | AppService **不**声明 `@Transactional`；写/读事务分别由 `AppServiceAspect`、`ReadOnlyTransactionAspect` 管理 |
| **禁止** | 读写 framework Setting（BR-CFG-PM-01） |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/src/main/java/com/openhis/health/application/lis/config/
├── LisSystemConfigAppService.java
├── command/
│   └── SaveLisConfigCommand.java
├── request/
│   └── ConfigSearchRequest.java
└── response/
    └── ConfigSearchResponse.java
```

| 依赖注入 | 类型 | 用途 |
|----------|------|------|
| `LisConfigDomainService` | 领域 | save / batchSave |
| `LisConfigQueryService` | 查询 | searchConfigs / getByKey |
| `AuditEventDomainService` | 领域（跨聚合） | BR-CFG-PM-03：记录 AuditLog |

### 2.2 领域方法白名单

| 聚合 | 方法 | 用于 IF |
|------|------|---------|
| LisConfig | `save(SaveLisConfigCommand)` | CFG-PM-02 |
| LisConfig | `batchSave(BatchSaveLisConfigCommand)` | CFG-PM-03 |
| LisConfigQueryService | `searchConfigs(Long tenantId, Pageable)` | CFG-PM-01 |
| LisConfigQueryService | `getByKey(Long tenantId, String key)` | CFG-PM-01 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

无新增领域枚举（KV 表，configKey = String）。

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

**HTTP 路径生成规则**：`POST /api/app/{serviceName}/{methodName}`，serviceName = `LisSystemConfigAppService` → `lisSystemConfig`

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
|----|---------------------|-----------|------|
| IF-CFG-PM-01 | `search` | 按 key 读 | `POST /api/app/lisSystemConfig/search` |
| IF-CFG-PM-02 | `save` | 保存 | `POST /api/app/lisSystemConfig/save` |
| IF-CFG-PM-03 | `batchimport | 批量导入 | `POST /api/app/lisSystemConfig/batchimport |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | 权限 / 租户 / 参数门禁 | 不再调用 |
| **Pre** | 写前只读：加载快照、Key 合法性校验 | 抛业务异常 |
| **Cmd** | 领域写：LisConfigDomainService#save | 事务回滚 |
| **Asm** | 审计日志记录 | — |

---

### 3.2 IF-CFG-PM-02 `save`（保存参数）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `configKey` | `String` | 是 | 键 |
| `configValue` | `String` | 是 | 值 |
| `remark` | `String` | 否 | 说明 |

#### Response

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `Long` | 保存后的 ID |

#### 失败码

| 失败码 | 场景 |
|--------|------|
| `CONFIG_KEY_INVALID` | key 为 framework Setting 前缀 |
| `DANGER_CONFIRM_REQUIRED` | 危险参数需二次确认 |

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
|------|------|------|----------|------|
| 1 | **Gate** | 参数空值门禁 | configKey / configValue 非空 | 若为空则抛 BusinessException |
| 2 | **Pre** | `LisConfigQueryService#getByKey(tenantId, configKey)` | tenantId + configKey | 校验 Key 非 framework Setting 前缀（BR-CFG-PM-01） |
| 3 | **Pre** | 危险参数二次确认 | configKey ∈ 危险列表 | BR-CFG-PM-02：若为合管/TAT 等关键参数，由前端或 Gate 传入确认标记 |
| 4 | **Cmd** | `LisConfigDomainService#save(SaveLisConfigCommand)` | configKey, configValue, remark | **写库点** |
| 5 | **Asm** | `auditEventDomainService.record(entityId, entityType, action)` | id, "LisConfig", "save" | BR-CFG-PM-03：审计落盘 |

---

### 3.3 IF-CFG-PM-01 `search`（按 key 读）

| 阶段 | 调用 | 说明 |
|------|------|------|
| **Pre** | `LisConfigQueryService#searchConfigs(tenantId, Pageable)` | 分页查询，**仅读路径** |

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `configKey` | `String` | 否 | 模糊匹配 |
| `pageNo` | `Integer` | 否 | 1-based |
| `pageSize` | `Integer` | 否 | 默认 20 |

---

### 3.4 IF-CFG-PM-03 `batchimport（批量导入）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
|------|------|------|----------|------|
| 1 | **Gate** | 批量上限门禁 | list.size ≤ 200 | 超上限则拒绝 |
| 2 | **Pre** | key 合法性批量校验 | 逐 key 校验 | BR-CFG-PM-01：任一个为 framework Setting key → 拒绝整批 |
| 3 | **Cmd** | `LisConfigDomainService#batchSave(BatchSaveLisConfigCommand)` | 全量 KV 对 | **写库点** |

---

## 4. QueryService 与领域事件

- **查询语义**：LisConfigQueryService 按 tenantId 隔离查询；orgId 可选。
- **领域事件**：`LisConfigChangedEvent` 由 AS 层编排发布（IF-CFG-PM-02 save 成功后），触发 INT 路由重算 / TAT 配置刷新。不在领域层发布。

---

## 5. 写库顺序（核心写接口视角）

| IF | App 方法 | Cmd 序号 | 领域调用 | 聚合写入顺序 |
|----|----------|----------|----------|-------------|
| CFG-PM-02 | save | §3.2 序号 4 | `LisConfigDomainService#save(SaveLisConfigCommand)` | 主表 lis_config 单行 upsert |
| CFG-PM-03 | batchImport | §3.4 序号 3 | `LisConfigDomainService#batchSave(BatchSaveLisConfigCommand)` | 主表 lis_config 批量 upsert |

**事务边界**：单 IF 内同一事务；失败全部回滚。由 `AppServiceAspect` 管理。

---

## 6. 非功能性需求实现

### 6.1 幂等性

- IF-CFG-PM-02：按 (tenantId, orgId, configKey) 唯一约束，重复 save → upsert（不抛异常）。

### 6.2 审计

- **实现**：跨聚合注入 `AuditEventDomainService`（同 `zxplat-domain/lis/auditevent`）
- **调用**：Asm 步骤 `auditEventDomainService.record(entityId, entityType, action)`
- **关联步骤**：§3.2 序号 5

---

## 7. 测试要点

### 7.1 功能测试

| 场景 | 测试要点 | 优先级 |
|------|----------|:--:|
| 正向 | 修改合管模式 → 生效 | P0 |
| 异常 | 写入 framework Setting key → 拒绝 | P0 |
| 异常 | 危险参数 → 未确认 → 拒绝 | P0 |

### 7.2 查询结果一致性

| 场景 | 验证点 |
|------|--------|
| search 分页 | pageNo=1, pageSize=20 返回正确 total |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v2.2 | 2026-06-22 | 去 Extension：`LisAuditLogExtension` → 直接注入 `AuditEventDomainService`（zxplat 内部，不走 Extension 层） |
| v2.0 | 2026-06-22 | 重新生成：修正事务口径（AppService 不声明 @Transactional）；修正包路径；补充 §2.3 枚举 |
| v1.0 | 2026-06-22 | 首版 |
