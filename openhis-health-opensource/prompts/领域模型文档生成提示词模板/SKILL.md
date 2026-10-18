---
name: 领域模型文档生成提示词模板
description: 生成/修订 ZXPlat 领域模型成稿（DM 九章）。落盘 docs/领域/DomainModel/。与文档代码一致性 Skill 双模式配合；触发：写 DM、补领域模型、DM-ADM/CLN/WKF 等。
---

# 领域模型文档（DM）生成 · ZXPlat

## 落盘

- 路径：`docs/领域/DomainModel/DM-{域码}-{序号}-{EnglishName}-{中文名}.md`
- 例：`docs/领域/DomainModel/DM-ADM-001-Patient-患者.md`
- SDD：`docs/领域/DomainModel-SDD/DM-…-SDD.md`（结构见 `领域SDD生成提示词模板`）

**目录约定**（与 whale-doc / 应用层 AS 对称）：

```text
docs/领域/
  DomainModel/          ← 每聚合一个 DM
  DomainModel-SDD/      ← 每聚合一个 SDD（有则有）
  缺口/
  专题/
```

**禁止**：`docs/领域/包/`；禁止为单个 Java 类型生成镜像 `.md`。  
**禁止**：未经用户触发本 Skill（或明确「写 DM」）时主动新建/扩写 DM；见 [`文档代码一致性`](../文档代码一致性/SKILL.md) §0。

## 九章骨架（必须）

| 章 | 标题 | 用途 |
|----|------|------|
| 1 | 概述 | 职责、范围、不做的事 |
| 2 | 聚合根与持久化形态 | 表、状态机 |
| 3 | 实体 | 字段表 |
| 4 | 值对象（含枚举） | 存储口径 |
| 5 | 领域事件 | 事件与载荷边界 |
| 6 | 领域服务 | §6.1 写 / §6.2 读（完整型 `####` 或简表型） |
| 7 | 不变式 | BR-* |
| 8 | 上下文映射 | 上下游 |
| 9 | 补充 | 变更、与别稿对齐 |

**禁止**在 DM 正文写应用编排、HTTP、包级依赖注入清单（属 AS/SDD）。

## 与代码一致

- 遵守 [`文档代码一致性`](../文档代码一致性/SKILL.md)：可文档先行或代码先行回写。  
- 模式 B 回写时：§6 方法名必须与 `*DomainService`/`*QueryService` public 方法对齐。  
- stub/draft：须含「已实现方法清单」或明确 DOC-DRIFT，禁止空头承诺。

## 包名

- Java：`com.zxplat.domain.{bc}.{agg}`  
- 事件：`com.zxplat.domain.share.events.{bc}.{topic}`  

## 配套

- 成码门禁：`代码生成通用规范`  
- 领域对准：`领域层代码生成提示词模板`  
