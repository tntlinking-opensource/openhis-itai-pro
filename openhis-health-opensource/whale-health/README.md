# Whale Health

基于 Whale 框架开发的健康领域服务应用。

## 项目结构

```
whale-health/
├── health-domain-shared/      # 领域共享模块（枚举、常量等）
├── health-domain/              # 领域层（实体、仓储接口）
├── health-application-contracts/  # 应用契约层（DTO）
├── health-application/         # 应用层（AppService）
├── health-infrastructure/     # 基础设施层（数据库迁移脚本等）
└── health-app/                # 应用启动模块
```

## 技术栈

- **框架**: Whale Framework 2.0.0-SNAPSHOT
- **Spring Boot**: 3.5.7
- **Java**: 17
- **数据库**: PostgreSQL
- **ORM**: JPA/Hibernate
- **数据库迁移**: Flyway

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.6+
- PostgreSQL 12+

### 2. 数据库配置

创建数据库：

```sql
CREATE DATABASE whale_health_db;
```

修改 `health-app/src/main/resources/application.yml` 中的数据库连接配置。

### 3. 运行应用

```bash
cd health-app
mvn spring-boot:run
```

### 4. 访问应用

- 应用地址: http://localhost:8081
- Swagger UI: http://localhost:8081/swagger-ui.html
- API 文档: http://localhost:8081/api-docs

## 模块说明

### health-domain-shared

领域共享模块，包含：
- 枚举类型（如 `HealthRecordStatus`）
- 常量定义（如 `HealthRecordConstants`）

### health-domain

领域层，包含：
- 实体类（如 `HealthRecord`）
- 仓储接口（如 `HealthRecordRepository`）

### health-application-contracts

应用契约层，包含：
- DTO 类（如 `HealthRecordDto`）
- 请求类（如 `CreateHealthRecordRequest`、`UpdateHealthRecordRequest`）
- 查询请求类（如 `HealthRecordQueryRequest`）

### health-application

应用层，包含：
- AppService 类（如 `HealthRecordAppService`）
- 业务逻辑实现

### health-infrastructure

基础设施层，包含：
- Flyway 数据库迁移脚本

### health-app

应用启动模块，包含：
- 启动类 `HealthApplication`
- 配置文件 `application.yml`

## 示例功能

### 健康档案管理

提供了完整的健康档案 CRUD 功能示例：

- **创建健康档案**: `POST /api/health-records`
- **获取健康档案详情**: `GET /api/health-records/{id}`
- **查询健康档案列表**: `GET /api/health-records`
- **更新健康档案**: `PUT /api/health-records/{id}`
- **删除健康档案**: `DELETE /api/health-records/{id}`

## 权限配置

应用使用 Whale 框架的权限系统，需要配置以下权限：

- `Health.HealthRecords.View` - 查看健康档案
- `Health.HealthRecords.Create` - 创建健康档案
- `Health.HealthRecords.Update` - 更新健康档案
- `Health.HealthRecords.Delete` - 删除健康档案

## 开发指南

### 添加新功能

1. 在 `health-domain-shared` 中添加枚举和常量
2. 在 `health-domain` 中添加实体和仓储接口
3. 在 `health-application-contracts` 中添加 DTO 和请求类
4. 在 `health-application` 中实现 AppService
5. 在 `health-infrastructure` 中添加数据库迁移脚本

### 使用 Whale 框架特性

- **多租户支持**: 实体实现 `IMultiTenant` 接口
- **审计功能**: 实体继承 `BaseAuditedEntity`
- **权限控制**: 使用 `@RequirePermission` 注解
- **分页查询**: 使用 `PagedResult` 和 `BaseQueryRequest`
- **规范查询**: 使用 `Specification` 构建动态查询条件

## 许可证

Copyright © OpenHIS

