# 首页布局优化实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 优化首页布局，固定总览卡在顶部，添加可移动、可自动隐藏的加号图标

**架构：** 将布局从单个 LazyColumn 改为 Column + LazyColumn 的组合，添加带状态机的可拖动 FAB 组件

**技术栈：** Jetpack Compose, Animatable, pointerInput, detectDragGestures

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `HomeScreen.kt` | 修改 | 主要改动文件，重构布局和 FAB |
| `HomeScreen.kt` | 新增 | `FabState` 枚举、`DraggableFab` 组件 |

---

## 任务 1：重构布局 - 固定总览卡

**文件：**
- 修改：`app/src/main/java/com/jiyixia/app/ui/screens/HomeScreen.kt:130-210`

- [ ] **步骤 1：将 MonthOverviewCard 从 LazyColumn 移出**

```kotlin
// 修改前（第 131-210 行）:
Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
    TopAppBar(...)
    
    LazyColumn(...) {
        item { MonthOverviewCard(...) }  // ← 在 LazyColumn 内
        if (uiState.pendingCount > 0) {
            item { PendingConfirmBanner(...) }  // ← 在 LazyColumn 内
        }
        // 记录列表...
    }
}

// 修改后:
Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
    TopAppBar(...)
    
    // 固定在顶部
    MonthOverviewCard(...)
    if (uiState.pendingCount > 0) {
        PendingConfirmBanner(...)
    }
    
    // 只有记录列表滚动
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        // 记录列表...
    }
}
```

- [ ] **步骤 2：运行构建验证**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：手动验证**

在模拟器或真机上测试：
- 总览卡始终可见
- 记录列表正常滚动
- 待确认 Banner 正确显示

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/java/com/jiyixia/app/ui/screens/HomeScreen.kt
git commit -m "feat: 固定总览卡在顶部，只有记录列表滚动"
```

---

## 任务 2：添加 FabState 枚举

**文件：**
- 修改：`app/src/main/java/com/jiyixia/app/ui/screens/HomeScreen.kt`（文件顶部添加）

- [ ] **步骤 1：添加 FabState 枚举**

在文件顶部（第 44 行之后）添加：

```kotlin
// ── FAB 状态枚举 ─────────────────────────────────────────────────────────────
private enum class FabState {
    NORMAL,   // 正常状态：52dp，100% 透明度
    SHRINK,   // 缩小状态：36dp，60% 透明度
    HIDDEN    // 隐藏状态：12dp，80% 透明度，贴边
}
```

- [ ] **步骤 2：运行构建验证**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/com/jiyixia/app/ui/screens/HomeScreen.kt
git commit -m "feat: 添加 FabState 枚举定义"
```

---

## 任务 3：实现可拖动 FAB 基础结构

**文件：**
- 修改：`app/src/main/java/com/jiyixia/app/ui/screens/HomeScreen.kt:214-226`

- [ ] **步骤 1：添加 FAB 状态和位置变量**

在 `HomeScreen` 函数内（第 110 行之后）添加：

```kotlin
// FAB 状态管理
var fabState by remember { mutableStateOf(FabState.NORMAL) }
var fabOffset by remember { mutableStateOf(Offset.Zero) }
var isDragging by remember { mutableStateOf(false) }
val density = LocalDensity.current

// 屏幕尺寸（用于边界计算）
var screenWidth by remember { mutableFloatStateOf(0f) }
var screenHeight by remember { mutableFloatStateOf(0f) }
```

- [ ] **步骤 2：添加屏幕尺寸测量**

修改最外层 Box（第 111 行），添加 onSizeChanged：

```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .onGloballyPositioned { coordinates ->
            screenWidth = coordinates.size.width.toFloat()
            screenHeight = coordinates.size.height.toFloat()
        }
) {
```

需要添加 import：
```kotlin
import androidx.compose.ui.layout.onGloballyPositioned
```

- [ ] **步骤 3：运行构建验证**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/java/com/jiyixia/app/ui/screens/HomeScreen.kt
git commit -m "feat: 添加 FAB 状态变量和屏幕尺寸测量"
```

---

## 任务 4：实现 FAB 状态定时器

**文件：**
- 修改：`app/src/main/java/com/jiyixia/app/ui/screens/HomeScreen.kt`（在 FAB 状态变量之后）

- [ ] **步骤 1：添加状态定时器**

在 FAB 状态变量之后添加：

```kotlin
// FAB 状态定时器
LaunchedEffect(fabState, isDragging) {
    if (isDragging) return@LaunchedEffect
    
    when (fabState) {
        FabState.NORMAL -> {
            delay(3000)  // 3秒后缩小
            fabState = FabState.SHRINK
        }
        FabState.SHRINK -> {
            delay(2000)  // 2秒后隐藏
            fabState = FabState.HIDDEN
        }
        FabState.HIDDEN -> {
            // 不自动恢复，需要用户点击
        }
    }
}
```

需要添加 import：
```kotlin
import kotlinx.coroutines.delay
```

- [ ] **步骤 2：运行构建验证**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/com/jiyixia/app/ui/screens/HomeScreen.kt
git commit -m "feat: 添加 FAB 状态定时器"
```

---

## 任务 5：实现吸附逻辑和边界约束

**文件：**
- 修改：`app/src/main/java/com/jiyixia/app/ui/screens/HomeScreen.kt`（在定时器之后）

- [ ] **步骤 1：添加吸附函数**

在定时器之后添加：

```kotlin
// 吸附到最近边缘
fun snapToEdge(currentOffset: Offset): Offset {
    val iconSize = when (fabState) {
        FabState.NORMAL -> 52f
        FabState.SHRINK -> 36f
        FabState.HIDDEN -> 12f
    }
    
    val distances = mapOf(
        "left" to currentOffset.x,
        "right" to screenWidth - currentOffset.x - iconSize,
        "bottom" to screenHeight - currentOffset.y - iconSize - 80f  // 80dp 底部导航栏
    )
    
    val nearest = distances.minByOrNull { it.value }?.key ?: "right"
    
    return when (nearest) {
        "left" -> Offset(4f, currentOffset.y.coerceIn(4f, screenHeight - iconSize - 80f))
        "right" -> Offset(
            (screenWidth - iconSize - 4f).coerceAtLeast(4f),
            currentOffset.y.coerceIn(4f, screenHeight - iconSize - 80f)
        )
        "bottom" -> Offset(
            currentOffset.x.coerceIn(4f, screenWidth - iconSize - 4f),
            screenHeight - iconSize - 84f
        )
        else -> currentOffset
    }
}

// 限制在屏幕边界内
fun constrainToScreen(offset: Offset, iconSize: Float): Offset {
    return Offset(
        offset.x.coerceIn(4f, screenWidth - iconSize - 4f),
        offset.y.coerceIn(4f, screenHeight - iconSize - 80f)
    )
}
```

- [ ] **步骤 2：运行构建验证**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/com/jiyixia/app/ui/screens/HomeScreen.kt
git commit -m "feat: 添加 FAB 吸附逻辑和边界约束"
```

---

## 任务 6：替换 FAB 为可拖动版本

**文件：**
- 修改：`app/src/main/java/com/jiyixia/app/ui/screens/HomeScreen.kt:214-226`

- [ ] **步骤 1：替换 FAB 实现**

删除原有的 FAB 代码（第 214-226 行）：

```kotlin
// 删除这段代码
// ── FAB（圆角矩形）- 导航到快速记账界面
Box(
    modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 18.dp, bottom = 82.dp)
        .size(52.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.primary)
        .clickable { onNavigateToQuickRecord() },
    contentAlignment = Alignment.Center
) {
    Icon(Icons.Default.Add, contentDescription = "记一笔", tint = Color.White, modifier = Modifier.size(26.dp))
}
```

替换为：

```kotlin
// ── 可拖动 FAB ──────────────────────────────────────────────────────────────
// 计算 FAB 尺寸和透明度
val fabSize = when (fabState) {
    FabState.NORMAL -> 52.dp
    FabState.SHRINK -> 36.dp
    FabState.HIDDEN -> 12.dp
}
val fabAlpha = when (fabState) {
    FabState.NORMAL -> 1f
    FabState.SHRINK -> 0.6f
    FabState.HIDDEN -> 0.8f
}
val fabCornerRadius = when (fabState) {
    FabState.NORMAL -> 16.dp
    FabState.SHRINK -> 12.dp
    FabState.HIDDEN -> 6.dp
}
val iconSize = when (fabState) {
    FabState.NORMAL -> 26.dp
    FabState.SHRINK -> 20.dp
    FabState.HIDDEN -> 8.dp
}

// 动画值
val animatedSize by animateDpAsState(
    targetValue = fabSize,
    animationSpec = tween(200),
    label = "fab_size"
)
val animatedAlpha by animateFloatAsState(
    targetValue = fabAlpha,
    animationSpec = tween(200),
    label = "fab_alpha"
)

// FAB 位置（如果没有拖动过，初始化到右下角）
val defaultOffset = Offset(
    screenWidth - with(density) { 52.dp.toPx() } - with(density) { 18.dp.toPx() },
    screenHeight - with(density) { 52.dp.toPx() } - with(density) { 82.dp.toPx() }
)
val currentOffset = if (fabOffset == Offset.Zero) defaultOffset else fabOffset

Box(
    modifier = Modifier
        .offset {
            IntOffset(currentOffset.x.roundToInt(), currentOffset.y.roundToInt())
        }
        .size(animatedSize)
        .alpha(animatedAlpha)
        .clip(RoundedCornerShape(fabCornerRadius))
        .background(MaterialTheme.colorScheme.primary)
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
                    isDragging = true
                    // 点击隐藏状态时展开
                    if (fabState == FabState.HIDDEN) {
                        fabState = FabState.NORMAL
                    }
                },
                onDragEnd = {
                    isDragging = false
                    // 吸附到最近边缘
                    fabOffset = snapToEdge(currentOffset)
                },
                onDragCancel = {
                    isDragging = false
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    val iconSizePx = when (fabState) {
                        FabState.NORMAL -> with(density) { 52.dp.toPx() }
                        FabState.SHRINK -> with(density) { 36.dp.toPx() }
                        FabState.HIDDEN -> with(density) { 12.dp.toPx() }
                    }
                    val newOffset = Offset(
                        currentOffset.x + dragAmount.x,
                        currentOffset.y + dragAmount.y
                    )
                    fabOffset = constrainToScreen(newOffset, iconSizePx)
                }
            )
        }
        .clickable {
            when (fabState) {
                FabState.NORMAL, FabState.SHRINK -> {
                    fabState = FabState.NORMAL
                    onNavigateToQuickRecord()
                }
                FabState.HIDDEN -> {
                    fabState = FabState.NORMAL
                }
            }
        },
    contentAlignment = Alignment.Center
) {
    if (fabState != FabState.HIDDEN) {
        Icon(
            Icons.Default.Add,
            contentDescription = "记一笔",
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}
```

需要添加 import：
```kotlin
import androidx.compose.animation.animateFloatAsState
import androidx.compose.animation.animateDpAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.mutableFloatStateOf
```

- [ ] **步骤 2：运行构建验证**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：手动验证**

在模拟器或真机上测试：
- FAB 可以拖动
- 3秒后缩小
- 5秒后隐藏
- 点击隐藏状态展开
- 拖动结束吸附到边缘

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/java/com/jiyixia/app/ui/screens/HomeScreen.kt
git commit -m "feat: 实现可拖动、可自动隐藏的 FAB"
```

---

## 任务 7：记账完成后重置 FAB 状态

**文件：**
- 修改：`app/src/main/java/com/jiyixia/app/ui/screens/HomeScreen.kt`

- [ ] **步骤 1：监听记账完成事件**

在 `HomeScreen` 函数中添加对 `uiState` 变化的监听：

```kotlin
// 记账完成后重置 FAB 状态
LaunchedEffect(uiState.records.size) {
    if (fabState != FabState.NORMAL) {
        fabState = FabState.NORMAL
    }
}
```

- [ ] **步骤 2：运行构建验证**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：手动验证**

测试：
- 记一笔账后，FAB 自动恢复到正常状态

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/java/com/jiyixia/app/ui/screens/HomeScreen.kt
git commit -m "feat: 记账完成后重置 FAB 状态"
```

---

## 任务 8：最终验证和清理

- [ ] **步骤 1：运行完整测试**

运行：`./gradlew testDebugUnitTest`
预期：BUILD SUCCESSFUL

- [ ] **步骤 2：运行构建**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：手动完整测试**

测试所有场景：
1. 总览卡固定在顶部
2. 记录列表正常滚动
3. FAB 可以拖动
4. FAB 3秒后缩小
5. FAB 5秒后隐藏
6. 点击隐藏状态展开
7. 拖动结束吸附到边缘
8. 记账完成后 FAB 恢复
9. 左滑删除正常工作

- [ ] **步骤 4：最终 Commit**

```bash
git add -A
git commit -m "feat: 首页布局优化完成 - 固定总览卡 + 可拖动 FAB"
```

---

## 自检清单

1. **规格覆盖度**
   - [x] 固定总览卡：任务 1
   - [x] FAB 状态机：任务 2, 4
   - [x] FAB 拖动：任务 3, 5, 6
   - [x] 记账后重置：任务 7

2. **占位符扫描**
   - [x] 无 "待定"、"TODO"
   - [x] 所有代码块完整
   - [x] 所有命令明确

3. **类型一致性**
   - [x] FabState 枚举在所有任务中一致
   - [x] 函数名 snapToEdge, constrainToScreen 一致
   - [x] 变量名 fabState, fabOffset, isDragging 一致
