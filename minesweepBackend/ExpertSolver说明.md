# ExpertSolver 求解器说明文档

## 1. 求解策略概述

`ExpertSolver` 是一个基于逻辑推理与概率计算的扫雷专家求解器。它的核心目标是在给定当前盘面视图的情况下，尽可能安全地推导出下一步的行动（标记地雷、点击安全格子，或在必须猜测时选择生还概率最大的格子）。

求解过程分为以下几个阶段：

### 1.1 确定性推理 (Deterministic Inference)
这是求解器的第一道防线，旨在不依赖任何猜测，纯粹通过逻辑推导出 100% 安全的格子或 100% 是雷的格子。
- **基础方程构建**：遍历盘面上所有已翻开的数字格子，根据其周围已知的雷数和未知的格子，构建线性方程（例如：`格子A + 格子B = 1`）。
- **全局雷数约束分析**：基于总剩余雷数与边界/内部格子的分布关系，推导边界格子和内部格子的雷数上下界。当所有剩余雷都在边界上时，内部格子全安全；反之亦然。当边界雷数能精确确定时，将其作为全局方程注入高斯消元，增强跨分量的推理能力。
- **单点排除**：如果一个方程中剩余雷数为 0，则该方程涉及的所有未知格子都是安全的；如果剩余雷数等于未知格子数，则这些格子全是雷。
- **子集推理 (Subset Reasoning)**：如果方程 A 的未知格子集合是方程 B 的严格子集，则可以通过相减得到新的约束。例如：`A + B = 1` 且 `A + B + C = 1`，则可以推导出 `C = 0`（C 是安全的）。
- **广义重叠推理 (Generalized Overlap)**：当两个方程存在交集但互不为子集时，通过交集与差集的最小/最大可行雷数区间，继续推出确定性安全格或地雷格。
- **高斯消元辅助推理 (Gaussian Elimination)**：对边界方程（含全局雷数约束方程）构造增广矩阵 `[A|b]` 做整数行消元，并对每行做上下界可行性分析，补充子集/重叠推理未覆盖的确定性结论。全局方程让高斯消元能利用跨连通分量的雷数约束。
- **概率-推理反馈迭代**：概率计算阶段如果发现新的确定性格子（概率为 0 或 1），会自动触发新一轮确定性推理，形成"概率→推理→概率"的迭代闭环，直到无新发现为止。
- **迭代求解**：上述过程会不断迭代，直到无法再推导出新的安全格子或地雷为止。

### 1.2 概率估计与猜测 (Probabilistic Guessing)
当确定性推理无法得出任何 100% 安全的格子时，求解器必须进行猜测。为了最大化胜率，求解器会计算每个未知格子的雷概率，并选择概率最低的格子。
- **连通分量划分**：将所有与数字格子相邻的未知格子（边界格子）划分为多个独立的连通分量。这样可以将一个巨大的组合问题分解为多个小问题，极大地降低了计算复杂度。
- **局部枚举 (Local Enumeration)**：对于每个连通分量，使用回溯法枚举所有合法的地雷分布情况。采用 MCV（最受约束变量优先）排序以提高回溯效率。
- **全局加权概率 (Global Weighted Probabilities)**：这是求解器的高级特性。它不仅考虑局部连通分量内的合法分布，还会结合**全局剩余雷数**进行动态规划 (DP) 加权。因为某些局部看似合法的分布，可能会导致全局雷数超标或不足，从而被排除或降低权重。全局 DP 使用大整数运算以确保精度。
- **部分精确 + 部分启发式融合**：若仅部分连通分量无法枚举（超过 `maxEnumCells` 上限），不再整体退化；成功分量继续用精确概率，失败分量使用约束感知的启发式概率回退，非约束区统一补齐概率。
- **启发式概率回退 (Heuristic Fallback)**：当全局精确加权不可用时，利用每个候选格周围数字约束估计局部雷概率，并用全局先验做缩放与平滑。
- **同概率格子的启发式选择**：当多个格子的雷概率相同时，求解器使用以下规则打破平局：
  1. 优先选择周围已翻开数字格子**最多**的格子（约束越强越好）。
  2. 优先选择周围未知格子**最少**的格子。
  3. 优先选择靠近盘面**中心**的格子。
- **信息增益加权猜测 (Information-Gain Weighted Guessing)**：在强制猜测时，求解器不仅考虑每个格子的雷概率，还通过信息增益权重对边界格子（有已翻开邻居）给予额外偏好。边界格子被揭开后能为周围的约束提供新信息，有助于减少后续猜测次数。有效评分公式为 `prob × (1 − infoWeight × revealedNeighbors / 8)`，通过微调该权重（默认 0.20）在单步生存率与长期信息收益之间取得平衡。
- **安全格子级联优化**：当存在多个已知安全格时，优先选择未知邻居最少的安全格（更可能为 0 值并触发级联揭开），最大化每次点击的信息收益。
- **角落开局**：首步固定为 `(0, 0)`。角落仅 3 个邻居，首步触发大级联（开空）的概率更高，有利于早期信息扩张。

---

## 2. 接口说明

### 2.1 核心接口与实现

- **`ExpertSolver`**（接口，`com.magichear.minesweepBackend.solver.ExpertSolver`）
  - `reset()`：清除已知安全格和已知雷格（用于新一局开始）。
  - `solveStep(int[][] view)`：核心求解接口。

- **`ExpertSolverImpl`**（默认实现，`com.magichear.minesweepBackend.solver.ExpertSolverImpl`）
  - 构造参数：`(int h, int w, int totalMines)` 或 `(int h, int w, int totalMines, int maxEnumCells, int maxValidAssignments)`
  - 实现 `ExpertSolver` 接口，包含确定性推理与概率估计的完整逻辑。

### 2.2 solveStep 返回值

返回 `SolveResult(String action, int[] cell, Map<Long, Double> cellProbs)`：
- `action`：`"safe"`（确定安全）、`"mine"`（确定是雷）、`"guess"`（猜测）。
- `cell`：推荐操作的格子坐标 `{row, col}`，或 `null`。
- `cellProbs`：未翻开格子的雷概率映射（cell-key → 概率），或 `null`。cell-key 编码为 `row * width + col`。

### 2.3 输入格式

`view` 为 AI 格式的盘面：
- `0-8`：已翻开的数字（周围雷数）
- `9`：未翻开

### 2.4 配置参数

在 `application.yml` 中通过 `solver` 前缀配置：

```yaml
solver:
  max-enum-cells: 100          # 单分量最大可枚举格子数
  max-valid-assignments: 500000 # 单分量最大合法分布数
  eval:
    games: 200                 # 评测每个难度的对局数
    seed: 20260221             # 随机种子
```

---

## 3. 注意事项与参数调整

- **计算复杂度控制**：全局概率计算和局部枚举是 NP-Hard 问题。为了保证求解速度，求解器内置了以下关键参数：
  - `maxEnumCells` (默认 100)：单个连通分量允许的最大未知格子数。如果超过此值，求解器将放弃对该分量的精确枚举，以防止耗时爆炸。
  - `maxValidAssignments` (默认 500000)：单个连通分量允许的最大合法分布数。达到此上限将提前截断搜索。
  - 回溯内部节点预算（当前固定为 5000000）：限制单分量搜索节点数，避免极端盘面下的长尾耗时。
  - **全局雷数剪枝**：回溯枚举时利用全局剩余雷数上界提前剪枝，跳过全局不可行的分支，提升枚举效率。
- **大整数运算**：全局 DP 加权阶段使用 `BigInteger` 进行精确计算，避免跨分量乘积导致的整数溢出。
- **性能与胜率的权衡**：如果你发现求解器在某些极端复杂的自定义盘面上运行过慢，可以适当调低上述两个参数；反之，如果你追求极限胜率且不在乎耗时，可以适当调高它们。

---

## 4. 评测

### 4.1 评测入口

评测代码位于 `src/test/java/.../solver/ExpertSolverEvalTest.java`，标记为 `@Tag("solver-eval")`，**不会**在默认构建中执行。

运行评测：
```bash
# 运行所有难度评测
mvn test -Psolver-eval

# 运行单个难度
mvn test -Psolver-eval -Dtest=ExpertSolverEvalTest#evaluateBeginner
mvn test -Psolver-eval -Dtest=ExpertSolverEvalTest#evaluateIntermediate
mvn test -Psolver-eval -Dtest=ExpertSolverEvalTest#evaluateExpert

# 运行全难度汇总
mvn test -Psolver-eval -Dtest=ExpertSolverEvalTest#evaluateAllAndPrintSummary
```

### 4.2 测试环境

评测使用独立的游戏环境 `SolverTestGame`（位于测试代码中），其规则为：
- 地雷随机放置
- 首步安全（踩雷则挪雷）
- 翻开零值格子自动扩散

此规则与后端主游戏服务 `GameService` 的规则（首击保证 3×3 安全区 + 空白格）不同，是独立的测试环境。

### 4.3 评测结果参考 (1000 局/档)

| 难度 | 尺寸 | 雷数 | SolverTest 胜率 | Backend 胜率 | 平均每局耗时 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| beginner | 9×9 | 10 | ~90% | ~96% | ~1ms |
| intermediate | 16×16 | 40 | ~78% | ~87% | ~8ms |
| expert | 16×30 | 99 | ~39% | ~46% | ~32ms |

> 注：SolverTest 环境首步仅保证单格安全；Backend 环境首步保证 3×3 安全区。由于不同随机数生成器产生的棋盘序列不同，具体胜率数字可能有 ±3% 的统计波动（200 局时波动更大，约 ±6%）。

---

## 5. 项目结构

```
src/main/java/.../solver/
├── ExpertSolver.java              # 求解器接口（SolveResult record、solveStep、reset）
├── ExpertSolverImpl.java          # 默认实现（确定性推理 + 概率估计 + 开局策略）
└── utils/
    ├── CellUtils.java             # 格子编解码、邻居枚举、已翻开/未翻开邻居计数
    ├── MathUtils.java             # 组合数 C(n,k)（BigInteger）、GCD
    ├── Equation.java              # 线性方程 record（一组格子 + 雷数）
    ├── GaussianElimination.java   # 高斯消元辅助推理
    ├── ComponentDiscovery.java    # 连通分量发现（Component record + BFS）
    ├── ComponentEnumerator.java   # 分量枚举（CompStats record + MCV 回溯）
    ├── GlobalProbabilityCalculator.java   # 全局加权概率（BigInteger DP）
    └── HeuristicProbabilityCalculator.java # 启发式概率回退

src/main/java/.../config/properties/
├── SolverProperties.java          # 求解器配置属性绑定
├── AiProperties.java
├── AppProperties.java
└── JwtProperties.java

src/test/java/.../solver/
├── ExpertSolverEvalTest.java      # 评测测试（@Tag("solver-eval")）
└── SolverTestGame.java            # 独立测试游戏环境
```
