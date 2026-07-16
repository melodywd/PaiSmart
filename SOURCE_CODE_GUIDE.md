# 源码阅读指南

本文档提供 PaiSmart（派聪明）项目的源码阅读顺序，帮助开发者快速理解系统架构和核心业务流程。

---

## 一、项目整体架构

```
用户端 → 前端(Vue3) → 后端(Spring Boot) → 存储层
                                      ├── MySQL (用户、文件元数据)
                                      ├── MinIO (文件存储)
                                      ├── Elasticsearch (向量索引)
                                      ├── Redis (会话、缓存)
                                      └── Kafka (异步处理)
                               → AI服务
                                      ├── DeepSeek API (LLM)
                                      └── Embedding API (向量化)
```

---

## 二、两条核心业务流程

### 流程1：文档上传与知识入库

```
用户上传 → UploadController → MinIO存储 → Kafka消息
                                                    ↓
                            FileProcessingConsumer (消费)
                                    ↓
                            ParseService (文档解析)
                                    ↓
                            VectorizationService (向量化)
                                    ↓
                            ElasticsearchService (索引存储)
```

### 流程2：RAG 聊天问答

```
用户提问 → WebSocket连接 → ChatHandler
                                    ↓
                            HybridSearchService (混合检索)
                                    ↓
                            构建Context + 引用映射
                                    ↓
                            LlmProviderRouter → DeepSeek API
                                    ↓
                            流式返回 + 保存对话历史
```

---

## 三、推荐源码阅读顺序

### 第1步：入口与配置（理解系统骨架）

理解项目的启动方式、安全配置和核心依赖。

| 文件 | 作用 |
|------|------|
| `src/main/java/com/yizhaoqi/smartpai/SmartPaiApplication.java` | Spring Boot 启动入口 |
| `src/main/java/com/yizhaoqi/smartpai/config/SecurityConfig.java` | 安全配置（JWT、角色权限） |
| `src/main/java/com/yizhaoqi/smartpai/config/JwtAuthenticationFilter.java` | JWT Token 验证过滤器 |
| `src/main/resources/application.yml` | 核心配置（数据库、Redis、Kafka、AI服务） |

### 第2步：用户认证与权限（理解访问控制）

理解系统的用户体系、认证机制和多租户权限隔离。

| 文件 | 作用 |
|------|------|
| `controller/AuthController.java` | 登录/注册 API |
| `config/OrgTagAuthorizationFilter.java` | 组织标签权限过滤 |
| `model/User.java` | 用户实体 |
| `model/OrganizationTag.java` | 多租户组织标签 |
| `service/CustomUserDetailsService.java` | 用户详情服务 |
| `service/OrgTagCacheService.java` | 组织标签缓存服务 |

### 第3步：文档上传流程（理解知识入库）

理解文档从上传到入库的完整异步处理流程。

| 文件 | 作用 |
|------|------|
| `controller/UploadController.java` | 文件上传 API |
| `service/UploadService.java` | 上传业务逻辑，MinIO 文件存储 |
| `consumer/FileProcessingConsumer.java` | Kafka 消费者，触发异步处理 |
| `model/FileProcessingTask.java` | 文件处理任务模型 |
| `model/FileUpload.java` | 文件上传记录实体 |

### 第4步：向量化与索引（理解 RAG 基础）

理解文档如何被解析、分块、向量化和索引存储。

| 文件 | 作用 |
|------|------|
| `service/ParseService.java` | 文档解析（使用 Apache Tika） |
| `service/VectorizationService.java` | 调用 Embedding API 生成向量 |
| `client/EmbeddingClient.java` | Embedding API 客户端 |
| `service/ElasticsearchService.java` | ES 索引操作封装 |
| `entity/EsDocument.java` | ES 文档结构定义 |
| `model/ChunkInfo.java` | 文档分块实体 |
| `model/DocumentVector.java` | 文档向量记录 |
| `repository/DocumentVectorRepository.java` | 向量数据访问 |

### 第5步：RAG 聊天流程（核心交互）

理解用户提问如何被处理，以及 RAG 检索增强生成的完整流程。

| 文件 | 作用 |
|------|------|
| `service/ChatHandler.java` | **核心类** - 处理 WebSocket 聊天，协调整个 RAG 流程 |
| `service/HybridSearchService.java` | 混合检索（关键词 + 语义向量） |
| `service/LlmProviderRouter.java` | LLM Provider 路由，支持多模型切换 |
| `client/DeepSeekClient.java` | DeepSeek API 客户端 |
| `service/ConversationService.java` | 对话历史管理 |
| `controller/ChatController.java` | 聊天相关 API（WebSocket Token 获取） |
| `entity/SearchResult.java` | 搜索结果实体 |

### 第6步：文档管理（理解知识库操作）

理解文档的增删改查、预览、索引重建等管理功能。

| 文件 | 作用 |
|------|------|
| `controller/DocumentController.java` | 文档管理 API |
| `service/DocumentService.java` | 文档删除、预览、重建索引 |
| `controller/SearchController.java` | 搜索 API |
| `service/TokenCacheService.java` | Token 缓存服务 |

### 第7步：用量管理与限流（理解资源控制）

理解系统的用量统计、配额管理和限流机制。

| 文件 | 作用 |
|------|------|
| `service/UsageQuotaService.java` | 用量配额服务 |
| `service/UsageBalanceQuotaService.java` | 余额配额服务 |
| `service/RateLimitService.java` | 限流服务 |
| `service/UsageDashboardService.java` | 用量仪表盘服务 |
| `model/DailyUsageStat.java` | 日用量统计 |
| `model/UserTokenRecord.java` | 用户 Token 使用记录 |

### 第8步：前端结构（理解用户界面）

理解前端的页面组织、状态管理和 API 调用方式。

| 目录/文件 | 作用 |
|-----------|------|
| `frontend/src/views/chat/` | 聊天界面 |
| `frontend/src/views/knowledge-base/` | 知识库管理界面 |
| `frontend/src/views/chat-history/` | 聊天历史（管理员） |
| `frontend/src/views/user/` | 用户管理（管理员） |
| `frontend/src/views/org-tag/` | 组织标签管理（管理员） |
| `frontend/src/service/` | API 调用封装 |
| `frontend/src/store/` | Pinia 状态管理 |
| `frontend/src/router/elegant/routes.ts` | 路由配置 |

---

## 四、关键设计要点

### 1. 异步处理
文件上传通过 Kafka 解耦，避免大文件处理阻塞用户请求。
- 上传后立即返回，后台异步处理
- 支持失败重试和死信队列

### 2. 混合检索
结合关键词检索和向量语义检索，提高召回质量。
- 关键词检索：精确匹配用户查询词
- 向量检索：语义相似度匹配
- 结果融合：综合排序返回最佳匹配

### 3. 多租户隔离
通过 `OrganizationTag` 实现数据权限隔离。
- 用户属于特定组织，只能访问组织内的文档
- 支持层级标签，继承上级权限
- 公开文档对所有用户可见

### 4. 流式响应
LLM 回复通过 WebSocket 实时推送，提升用户体验。
- 用户提问后立即开始接收回复
- 支持中断停止回复
- 完成后保存完整对话历史

### 5. 引用追溯
AI 回答附带来源文档引用，增强可解释性。
- 回复中标注引用编号 `[1] [2]`
- 引用映射记录文档 MD5、页码、匹配内容
- 支持点击引用跳转到原文

---

## 五、快速入口建议

**从哪个文件开始？**

- 想了解 **聊天交互**：从 `ChatHandler.java` 开始
- 想了解 **文档处理**：从 `FileProcessingConsumer.java` 开始
- 想了解 **认证授权**：从 `SecurityConfig.java` 开始
- 想了解 **整体配置**：从 `application.yml` 开始

**核心类速览：**

| 核心类 | 一句话描述 |
|--------|-----------|
| `ChatHandler` | RAG 流程的总协调者，处理用户提问到 AI 回复的完整链路 |
| `HybridSearchService` | 检索引擎，融合关键词和向量搜索 |
| `VectorizationService` | 文档向量化，连接文本与 AI |
| `FileProcessingConsumer` | 异步处理入口，驱动文档入库流程 |
| `LlmProviderRouter` | 多模型路由，支持切换不同 LLM |

---

## 六、目录结构概览

```
src/main/java/com/yizhaoqi/smartpai/
├── SmartPaiApplication.java      # 启动入口
├── client/                       # 外部 API 客户端
│   ├── DeepSeekClient.java       # DeepSeek LLM
│   └ EmbeddingClient.java        # Embedding API
├── config/                       # 配置类
│   ├── SecurityConfig.java       # 安全配置
│   ├── JwtAuthenticationFilter.java
│   ├── OrgTagAuthorizationFilter.java
├── consumer/                     # Kafka 消费者
│   └ FileProcessingConsumer.java
├── controller/                   # REST API
│   ├── AuthController.java
│   ├── ChatController.java
│   ├── DocumentController.java
│   ├── UploadController.java
├── entity/                       # 实体类
│   ├── EsDocument.java           # ES 文档
│   ├── SearchResult.java         # 搜索结果
├── handler/                      # WebSocket 处理器（如有）
├── model/                        # 数据模型
│   ├── User.java
│   ├── FileUpload.java
│   ├── OrganizationTag.java
│   ├── ChunkInfo.java
├── repository/                   # 数据访问层
├── service/                      # 业务逻辑层
│   ├── ChatHandler.java          # 核心 RAG 处理
│   ├── HybridSearchService.java
│   ├── VectorizationService.java
│   ├── DocumentService.java
│   ├── ParseService.java
├── utils/                        # 工具类

frontend/src/
├── views/                        # 页面组件
│   ├── chat/
│   ├── knowledge-base/
│   ├── user/
├── service/                      # API 调用
├── store/                        # Pinia 状态管理
├── router/                       # 路由配置
```