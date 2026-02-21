# 🎮 Minesweeper — 智能扫雷

一个全栈扫雷游戏，集成智能求解器辅助预测和自动化测试功能。

![alt text](image.png)

## 求解器胜率情况（辅助强度）

扫雷游戏内置两种规则

|Difficulty|SolverTest (1-cell)|Backend (3×3 safe)|Δ (pp)|
|--|--|--|--|
|beginner|90.40%|95.80%|+5.40|
|intermediate|77.90%|86.60%|+8.70|
|expert|39.20% |45.90%|+6.70 |

SolverTest = first click: single cell safe (mine relocated)
Backend    = first click: 3×3 safe zone, always blank (0)
Δ (pp)     = Backend win rate - SolverTest win rate (percentage points)

## 技术栈

| 层 | 技术 | 版本 |
|---|------|------|
| **后端** | Spring Boot / Java | 4.0.2 / 25 |
| **前端** | React / TypeScript / Vite | 19 / 5.8 / 6.3 |
| **数据库** | H2 (file) | — |
| **序列化** | Jackson 3.x (`tools.jackson`) | — |

## 项目结构

```
minesweep/
├── minesweepBackend/   # Spring Boot 后端 (包含 ExpertSolver 智能求解器)
├── minesweepFrontend/  # React 前端
├── build.ps1           # 一键构建脚本
├── run.ps1             # 一键启动 (PowerShell)
└── run.bat             # 一键启动 (CMD)
```

## 功能特性

### 🕹️ 核心游戏
- 三种难度：简单 (9×9, 10雷)、中等 (16×16, 40雷)、困难 (16×30, 99雷)
- 首次点击必定安全 (空白格 + 自动扩散)
- 右键标旗、计时器、剩余雷数显示

### 🤖 智能辅助 (ExpertSolver)
- 基于确定性推理与概率计算的 Java 原生求解器 (支持所有难度)
- **切换模式**：开启后自动在每次揭开后显示概率热力图 + 最安全格高亮
- 关闭时完全隐藏辅助信息

### 🧪 求解器自动化测试
- 命名测试批次，自动运行 100 局游戏
- 实时进度条 + 轮询状态
- 记录胜率、平均/最长/最短耗时
- 历史测试结果持久化，可滚动查看

### 👤 用户系统
- JWT 无状态认证 
- SHA-256 × 1000 轮 + 随机盐值密码哈希
- 注册/登录，默认用户 `magichear` / `111`
- 前端 Token 自动管理，401 自动登出

### 📊 统计面板
- 全局总局数 & 胜率
- 各难度详细统计：总局/总胜/胜率/最大连胜/最快/最慢用时
- Top 3 最快通关记录
- 求解器测试历史记录

## 快速开始

### 前置条件

- **Java 25+**
- **Node.js 18+** & npm

### 构建

```powershell
# 一键构建 (前端 + 后端打包)
.\build.ps1
```

或手动构建：

```powershell
# 前端
cd minesweepFrontend
npm install
npm run build

# 后端 (含测试)
cd minesweepBackend
.\mvnw.cmd package
```

### 运行

```powershell
# PowerShell (推荐)
.\run.ps1

# 或 CMD
.\run.bat
```

启动后将自动：
1. 启动 Spring Boot 后端 (端口 8080)
2. 检测后端就绪后 **自动打开浏览器**

### 开发模式

```powershell
# 终端 1: 后端
cd minesweepBackend
.\mvnw.cmd spring-boot:run

# 终端 2: 前端 (Vite dev server, 端口 5173, 自动代理 /api → 8080)
cd minesweepFrontend
npm run dev
```

## API 接口

### 认证 (无需 Token)
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/register` | 注册 |

### 游戏 (需 Bearer Token)
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/game/new` | 创建新游戏 |
| GET  | `/api/game/{id}` | 获取游戏状态 |
| POST | `/api/game/{id}/reveal` | 揭开格子 |
| POST | `/api/game/{id}/flag` | 切换旗帜 |
| POST | `/api/game/{id}/predict` | 求解器预测 |

### 统计 (需 Bearer Token)
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/stats` | 获取全部统计 |

### 求解器测试 (需 Bearer Token)
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ai-test/start` | 启动测试 |
| GET  | `/api/ai-test/status/{id}` | 查询进度 |
| GET  | `/api/ai-test` | 获取所有历史记录 |

## 测试

```powershell
cd minesweepBackend
.\mvnw.cmd test
```

可选参数 `-Psolver-eval` ，加入后则包含隐藏的求解器性能测试

## 许可

MIT
