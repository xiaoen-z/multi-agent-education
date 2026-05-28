# Java 版 -- 多Agent智能教育系统

> 基于 Spring Boot 3 + Spring AI + Spring Event 的实现

## 技术栈

- **框架**: Spring Boot 3.4 + Spring AI 1.0
- **Agent通信**: Spring ApplicationEventPublisher（发布-订阅）
- **异步处理**: @Async + ThreadPoolTaskExecutor
- **数据库**: H2（开发） / PostgreSQL（生产）
- **构建**: Maven + Java 21

## 与 Python 版的对比

| 维度 | Python 版 | Java 版 |
|------|----------|---------|
| 事件总线 | 自定义 EventBus (asyncio) | Spring ApplicationEvent |
| 异步 | async/await | @Async + 线程池 |
| 依赖注入 | 手动构造 | Spring IoC 容器 |
| 线程安全 | asyncio 单线程 | ConcurrentHashMap |

## 快速开始

```bash
# 需要 Java 21+ 和 Maven 3.9+
cd java
mvn clean install -DskipTests
mvn spring-boot:run

# 访问 http://localhost:8080/api/v1/health
```

## 目录结构

```
java/src/main/java/com/edu/agent/
├── EduAgentApplication.java     # Spring Boot 入口
├── agent/                       # 5个Agent
│   ├── AssessmentAgent.java     # @EventListener 实现
│   ├── TutorAgent.java
│   ├── CurriculumAgent.java
│   ├── HintAgent.java
│   └── EngagementAgent.java
├── core/                        # 核心模块
│   ├── AgentEvent.java          # 事件模型
│   ├── EventType.java           # 事件类型枚举
│   ├── LearnerModel.java        # BKT 知识追踪
│   └── SpacedRepetition.java    # SM-2 算法
├── controller/
│   └── EduController.java       # REST API
└── config/
    └── AsyncConfig.java         # 异步配置
```
