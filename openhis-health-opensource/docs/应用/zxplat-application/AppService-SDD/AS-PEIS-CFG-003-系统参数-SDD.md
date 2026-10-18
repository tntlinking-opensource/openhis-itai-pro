# AS-PEIS-CFG-003 系统参数 · 应用服务详细设计（SDD）

> **编码**：AS-PEIS-CFG-003-SDD · **版本**：v1.0 · **日期**：2026-06-27  
> **对应 AS**：AS-PEIS-CFG-003-系统参数.md（v1.1）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `health-application/peis/config/settings` |
| **类** | `PeisModuleSettingsAppService` |
| **方法权威** | DM-PEIS-004 |
| **实现阶段口径** | **目标态全量契约**（P0：CFG-13） |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/.../peis/config/settings/
├── PeisModuleSettingsAppService.java
├── request/SaveConfigRequest.java
├── response/SearchConfigResponse.java
└── mapper/PeisModuleSettingsMapper.java + PeisModuleSettingsMapperImpl.java
```

### 2.2 领域方法白名单

| 服务 | 方法 | IF |
|------|------|-----|
| PeisConfigDomainService | `save(SavePeisConfigCommand)` | CFG-13 |
| PeisConfigQueryService | `getByKey(tenantId, key)` | CFG-13 读 |
| PeisConfigQueryService | `search(PeisConfigQuery)` | CFG-13 列表 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|-------------|------|
| peis.reg.charge_gate | `SaveConfigRequest.configKey` | DM-PEIS-004 §2 | REG-08 门禁；目标态 `PeisConfigKeys.REG_CHARGE_GATE` |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口

**HTTP**：`PeisModuleSettingsAppService` → `peisModuleSettings`

| IF | 方法 | 中文 | HTTP |
|----|------|------|------|
| CFG-13 | `searchConfig` | 查询参数 | `POST /api/app/peisModuleSettings/searchConfig` |
| CFG-13 | `saveConfig` | 保存参数 | `POST /api/app/peisModuleSettings/saveConfig` |

### 3.2 IF-PEIS-CFG-13 `saveConfig`

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `configKey` | `String` | 是 |
| `configValue` | `String` | 是 |

#### 失败码

| 码 | 场景 |
|----|------|
| `CONFIG_KEY_UNKNOWN` | 非法 key |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | configKey 在白名单 | |
| 2 | Cmd | `PeisConfigDomainService#save(SavePeisConfigCommand)` | **写库点** peis_config |
| 3 | Asm | AuditLog | BR-CFG3-01 |

---

## 4. QueryService 与领域事件

- saveConfig 后 REG/DES 通过 `PeisConfig`QueryReader`（规划） 读最新 charge_gate。

---

## 5. 写库顺序

| IF | 表 |
|----|-----|
| saveConfig | peis_config |

---

## 6. 非功能性需求实现

### 6.1 幂等性

- 同 tenantId+key 覆盖写。

### 6.2 并发控制

- 乐观锁。

### 6.3 重试与容错

- 无。

### 6.4 数据脱敏

- 敏感 configValue 响应掩码。

### 6.5 审计日志

- saveConfig 记变更前后值（BR-CFG3-01）。

---

## 7. 测试要点

### 7.1 功能测试

| 场景 | 层级 | 自动化 | 优先级 |
|------|------|--------|:--:|
| charge_gate | REG-08 读开关 | 功能 | 集成 | P0 |
| 非法 key | CONFIG_KEY_UNKNOWN | 功能 | API | P0 |

### 7.2～7.5

- 审计日志可查；无 P0 缺口。

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-06-27 | 对齐 AS v1.1；Gap 表 |
| v1.0 | 2026-06-27 | 首版 |
