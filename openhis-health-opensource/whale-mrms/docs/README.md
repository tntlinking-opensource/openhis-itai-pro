# Whale MRMS 文档

> 病案管理（Medical Records Management System）模块设计文档。写业务代码前以 **功能清单** 为范围真源，概要/详细设计在清单评审通过后再展开。

## 文档索引

| 文档 | 说明 |
|------|------|
| [00-功能清单.md](./00-功能清单.md) | **功能范围、优先级、模块边界、开发排期** |
| [01-架构设计.md](./01-架构设计.md) | **系统上下文、ADR、聚合、Port 集成、Wave** |
| [02-概要设计.md](./02-概要设计.md) | **表结构、类清单、Port/API 契约、Wave 检查表** |
| [03-病案首页-详细设计.md](./03-病案首页-详细设计.md) | **首页全字段、V2 扩列、自动取值、校验、4401 映射** |
| [04-归档与质控-详细设计.md](./04-归档与质控-详细设计.md) | **质控规则、缺陷闭环、归档锁定、卷宗 PDF、逾期预警** |
| [05-对外上报-详细设计.md](./05-对外上报-详细设计.md) | **医保 4401 组装、invoke、上传日志、批量任务** |
| [06-病案首页-前端设计.md](./06-病案首页-前端设计.md) | **openhis-pro-ui：菜单、列表、左锚点全屏编辑、编码 Picker、OpenPDF 打印** |

## 上游参考

| 来源 | 路径 |
|------|------|
| 系统需求（病案管理） | `whale-doc/cmmi3ddd/03-需求规格/系统需求规格说明书-SRS.md` §3.17 FR-MED |
| 就诊历史 → 病案消费 | `whale-doc/cmmi3ddd/02-通用语言与领域建模/DomainModel/DM-CUS-004-EncounterHistory-就诊历史.md` |
| EMR 功能边界 | `whale-doc/emr/功能清单.md` §14（首页/归档归属 MRMS） |
| 医保病案首页上传 | `whale-chs/docs/apis/44xx/4401-住院病案首页信息.md` |
| 应用服务规划 | `whale-doc/.../应用服务设计清单-可勾选.md` → `AS-EXT-MR-001`、`AS-IP-DOR-004` |

## 模块代码

```text
whale-mrms/
├── mrms-domain/       com.openhis.whale.mrms.domain
└── mrms-application/  com.openhis.whale.mrms.application
```
