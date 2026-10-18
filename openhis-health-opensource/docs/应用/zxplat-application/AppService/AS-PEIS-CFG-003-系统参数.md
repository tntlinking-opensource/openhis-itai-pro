# 体检域 · 系统参数 · 应用服务设计（AS）

> **编码**：AS-PEIS-CFG-003 · **版本**：v1.1 · **日期**：2026-06-27  
> **菜单**：系统参数 · **类名**：`PeisModuleSettingsAppService` · **路由**：`/peis/config/settings`

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | `peis_config` KV 参数（P0）；业务字典/PDF 参数（P1，可扩展 key） |
| **在外** | framework Setting；LIS `lis_config` 独立 Module |
| **涉及域** | [DM-PEIS-004](./DM-PEIS-004-PeisConfig-模块参数.md) |

### 1.2 角色

| 角色 | 动作 |
|------|------|
| **管理员** | 维护模块参数 |
| **REG/DES/INT** | 读 Reader 消费配置 |

### 1.3 术语

| key（P0 示例） | 影响 |
|----------------|------|
| `peis.reg.charge_gate` | REG-08 未收费阻塞分科 |
| `peis.reg.auto_dispatch` | 登记后自动 **INT-01**（本院 WorkItem；金域由 LIS EXT） |
| `peis.report.template_code` | REP 默认模板 |
| `peis.bus_no.rule` | 体检流水号 |

### 1.4 范围

| P0 | CFG-13 |
| P1 | CFG-14 字典、CFG-15 PDF（扩展 key 或独立页） |

### 1.6 业务规则（BR）

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-CFG3-01** | 敏感项变更 AuditLog | CFG-13 |
| **BR-CFG3-02** | `peis.reg.charge_gate` → REG-08 | CFG-13 |
| **BR-CFG3-03** | configKey 白名单；未知 key 拒绝 | CFG-13 |
| **BR-CFG3-04** | 禁止与 framework Setting key 冲突 | CFG-13 |

---

## 2. 页面与接口总览

| IF | 动作 | 读写 | 关键规则 |
|----|------|:--:|----------|
| **IF-PEIS-CFG-13** | 查询/保存模块参数 | 读/写 | BR-CFG3-01～04 |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
|--------|----------|-------------|-------------------|----------|---------|--------|------|
| GAP-PEIS-CFG3-01 | **DM 能力** | [DM-PEIS-004](./DM-PEIS-004-PeisConfig-模块参数.md) §5 | `PeisConfigDomainService#save` / `PeisConfigQueryService` | DM+代码 | CFG-13 | P0 | ✅ 已关闭（领域 + PeisModuleSettingsAppService，2026-07-19） |

---

## 4. 接口编排说明书

### 4.1 IF-PEIS-CFG-13 searchConfig / saveConfig

**searchConfig**

| 步骤 | 说明 |
|------|------|
| 1 | `PeisConfigQueryService#search` 或按 key 列表 |
| 2 | 敏感 value 掩码返回 |

**saveConfig**

| 步骤 | 说明 |
|------|------|
| 1 | Gate：configKey 白名单（BR-CFG3-03） |
| 2 | Cmd：`PeisConfigDomainService#save` |
| 3 | AuditLog（BR-CFG3-01） |

---

## 5. 业务异常

| 码 | 场景 |
|----|------|
| `CONFIG_KEY_UNKNOWN` | 非法 key |
| `CONFIG_VALUE_INVALID` | 值格式错误（如 charge_gate 非 boolean） |

---

## 6. 事件

save 后 REG/DES 读 Reader 缓存失效（TTL 或事件 P2）。

---

## 7. NFR

租户级 key 唯一；乐观锁；敏感值审计。

---

## 8. 测试

- 改 charge_gate → REG-08 行为变化
- auto_dispatch=true → REG-02 后触发 INT-01
- 非法 key → CONFIG_KEY_UNKNOWN

---

## 9. 关联文档

- SDD：[AS-PEIS-CFG-003-SDD](../AppService-SDD/AS-PEIS-CFG-003-系统参数-SDD.md)
- REG：[AS-PEIS-REG-001](./AS-PEIS-REG-001-个检登记.md)
- INT：[AS-PEIS-INT-001](./AS-PEIS-INT-001-LIS-PACS外发.md)
- DM：[DM-PEIS-004](./DM-PEIS-004-PeisConfig-模块参数.md)

| v1.1 | 2026-06-27 | 9 章齐；Gap；auto_dispatch 口径 |
| v1.0 | 2026-06-27 | 骨架 |
