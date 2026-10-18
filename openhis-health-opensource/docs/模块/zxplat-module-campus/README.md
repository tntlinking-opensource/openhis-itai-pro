# ZXPlat Campus 文档索引

> 高校校医院 **校务组织**、**学生/教职工档案**、**公费医疗** 可选增值包：**zxplat-module-campus**（代码待创建）  
> **文档目录**：`docs/模块/zxplat-module-campus/`  
> **代码目标**：`zxplat-plugin/zxplat-module-campus/`  
> **架构真源**：[01-架构/模块化开发规范.md](../01-架构/模块化开发规范.md) §12  
> **成码门禁**：[07-成码前Checklist.md](./07-成码前Checklist.md)  
> **版本**：v1.9 · **日期**：2026-07-19  
> **成码决策**：[ADR-CMP-001](./ADR-CMP-001-成码前决策.md)  
> **v1.9**：health 已移除 `adm_patient_student`；学籍仅 `cmp_patient_student`；成码前决策 Accepted

---

## 阅读顺序

| 序号 | 文档 | 说明 |
|------|------|------|
| **00** | [00-功能清单.md](./00-功能清单.md) | **范围与排期真源** |
| **01** | [01-概要设计.md](./01-概要设计.md) | HLD：边界、领域、配置 |
| **02** | [02-交互说明.md](./02-交互说明.md) | 菜单、页面、用户流程 |
| **03** | [03-AppService详细设计.md](./03-AppService详细设计.md) | AppService 索引 |
| **AS** | [AppService/](./AppService/) | **IF 契约（成码）** |
| **AS-SDD** | [AppService-SDD/](./AppService-SDD/) | 实现映射 |
| **DM** | [DomainModel/](./DomainModel/) | 领域模型 |
| **DM-SDD** | [DomainModel-SDD/](./DomainModel-SDD/) | 领域实现 |
| **06** | [06-Flyway-表结构.md](./06-Flyway-表结构.md) | 表 / DDL |
| **07** | [07-成码前Checklist.md](./07-成码前Checklist.md) | **成码门禁** |
| **ADR** | [ADR-CMP-001-成码前决策.md](./ADR-CMP-001-成码前决策.md) | **成码前决策（Accepted）** |
| **08** | [08-Contracts与外部依赖.md](./08-Contracts与外部依赖.md) | Reader 契约 |
| **09** | [09-文档清单.md](./09-文档清单.md) | 清单 |
| — | [模块说明-代码结构.md](./模块说明-代码结构.md) | Maven 规划 |

---

## 代码（目标态）

```text
zxplat-plugin/zxplat-module-campus/   （Batch A 已建）
├── module-campus-domain/
├── module-campus-bridge/
├── module-campus-application/
└── module-campus-starter/          # Flyway: db/migration/campus
```

Profile：`-Pwith-campus` · `zxplat.modules.campus.enabled=true`

---

## 装配

```yaml
zxplat:
  modules:
    campus:
      enabled: true
```

非高校院点默认 `enabled=false`。与 Peis / 收银衔接见 [08-Contracts](./08-Contracts与外部依赖.md)。

---

## 成码建议批次

1. **P0-1** 组织三 AS + `CampusOrgQueryReader` + Flyway 组织表  
2. **P0-2** 学生/教职工/变更/资格  
3. **P0-3** 公费配置/比例/自建审批/试算 Reader（收银改草稿见 ADR D 预留）

首批范围与口径以 [ADR-CMP-001](./ADR-CMP-001-成码前决策.md) 为准（NENU · 范围 C + D TODO）。