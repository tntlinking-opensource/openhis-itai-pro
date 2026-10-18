# 长春朝阳区医院 · 银联支付院点文档

> **院点名称**：长春市朝阳区中医院  
> **定点机构编码**：`H22010402403`  
> **启动工程**：`zxplat-plugin/whale-project/project-H22010402403`  
> **目标上线日**：**2026-07-30**（与 [吉林朝阳中医院](../../吉林朝阳中医院/README.md) 一致）

---

## 文档索引

| 文档 | 说明 |
| --- | --- |
| **[00-功能清单.md](./00-功能清单.md)** | **院点银联功能范围、P0/P1 裁剪、老系统映射、验收**（本文档目录主入口） |

## 上游真源

| 文档 | 用途 |
| --- | --- |
| [../00-功能清单.md](../00-功能清单.md) | Whale UnionPay-BPC 模块通用功能清单 |
| [../01-概要设计.md](../01-概要设计.md) | 双通道、Extension、数据模型 |
| [../05-BPC接口对照表.md](../05-BPC接口对照表.md) | BPC HTTPS JSON 报文 |
| [../07-院点配置与上线手册.md](../07-院点配置与上线手册.md) | mid/tid、contract.properties、上线步骤 |
| [../06-P0手工测试清单.md](../06-P0手工测试清单.md) | 模块级手工测试 |
| [../03-详细设计与编码指导.md](../03-详细设计与编码指导.md) | **成码真源**（§3.5 英文命名已同步本文） |
| [../02-交互说明.md](../02-交互说明.md) | UI「扫码支付」（已同步本文） |
| [../../.cursor/rules/whale-unionpay-bpc-codegen.mdc](../../.cursor/rules/whale-unionpay-bpc-codegen.mdc) | Cursor 成码规则（已同步本文） |
| [../../吉林朝阳中医院/00-上线前Checklist.md](../../吉林朝阳中医院/00-上线前Checklist.md) §7 / §14.4 | 院点整体验收中的银联条目 |

## 现场参考（老 HIS）

| 路径 | 说明 |
| --- | --- |
| `whale-project/project-H22010402403/myareahisnyb` | 现网 JSP 收银与银联前端逻辑 |
| `whale-project/project-H22010402403/UnionPayPlugin` | 现网本机插件（`127.0.0.1:19994`） |
| [../unionpay-bpc-client/README.md](../unionpay-bpc-client/README.md) | 插件源码与通道 B 说明 |

---

## 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.2 | 2026-06-23 | 配置改 unionpay-bpc-contract.properties；已同步 03 / codegen |
| v1.0 | 2026-06-23 | 初稿：院点功能清单 |
