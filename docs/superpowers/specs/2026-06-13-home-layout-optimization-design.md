# 首页布局优化设计

**日期**: 2026-06-13
**状态**: 已批准

## 背景

用户反馈两个问题：
1. 首页向上滑动时，总览数据也会跟着滚动，不方便查看
2. 加号图标固定在右下角，遮挡数据且不够灵活

## 设计目标

1. 总览数据固定在顶部，只有记录列表滚动
2. 加号图标可以拖动，长时间不用自动隐藏到侧边

---

## 需求1: 固定总览卡

### 当前布局
```
Column {
    TopAppBar
    LazyColumn {
        MonthOverviewCard  ← 会跟着滚动
        PendingConfirmBanner
        记录列表...
    }
}
```

### 目标布局
```
Column {
    TopAppBar              ← 固定
    MonthOverviewCard      ← 固定
    PendingConfirmBanner   ← 固定（可选）
    LazyColumn {           ← 只有这里滚动
        记录列表...
    }
}
```

### 实现要点

1. 将 `MonthOverviewCard` 从 LazyColumn 中移出
2. 将 `PendingConfirmBanner` 从 LazyColumn 中移出
3. LazyColumn 只保留记录列表
4. 移除 LazyColumn 的 `contentPadding(bottom = 88.dp)`，改为 Column 的 padding

---

## 需求2: 可移动加号图标

### 状态机设计

```
┌─────────────────────────────────────────────────────┐
│                     状态转换                         │
├─────────────────────────────────────────────────────┤
│  [正常] ──3秒无操作──→ [缩小] ──2秒无操作──→ [隐藏]  │
│    ↑                   │                    │       │
│    └──点击/记录/拖动────┴────────────────────┘       │
└─────────────────────────────────────────────────────┘
```

### 各状态规格

| 状态 | 大小 | 透明度 | 位置 | 形状 | 阴影 |
|------|------|--------|------|------|------|
| 正常 | 52dp | 100% | 用户拖动位置 | 圆角矩形(16dp) | 有 |
| 缩小 | 36dp | 60% | 同上 | 圆角矩形(12dp) | 轻 |
| 隐藏 | 12dp | 80% | 贴边（左/右/下） | 圆形 | 无 |

### 交互行为

| 操作 | 行为 |
|------|------|
| 点击（正常/缩小状态） | 导航到快速记账页面 |
| 点击（隐藏状态） | 展开回正常状态 |
| 长按 | 开始拖动 |
| 拖动中 | 图标跟随手指移动 |
| 拖动结束 | 吸附到最近的屏幕边缘 |
| 记账完成后 | 自动回到正常状态，位置不变 |

### 吸附逻辑

```kotlin
fun snapToEdge(offset: Offset, screenWidth: Float, screenHeight: Float, iconSize: Float): Offset {
    val distances = mapOf(
        "left" to offset.x,
        "right" to screenWidth - offset.x - iconSize,
        "top" to offset.y,
        "bottom" to screenHeight - offset.y - iconSize
    )
    val nearest = distances.minByOrNull { it.value }!!.key
    
    return when (nearest) {
        "left" -> Offset(4f, offset.y)
        "right" -> Offset(screenWidth - iconSize - 4f, offset.y)
        "top" -> Offset(offset.x, 4f)
        "bottom" -> Offset(offset.x, screenHeight - iconSize - 4f)
        else -> offset
    }
}
```

### 边界约束

- 图标不能超出屏幕边界
- 隐藏状态下，至少保留 12dp 可见
- 避免遮挡底部导航栏

### 定时器逻辑

```kotlin
// 状态定时器
LaunchedEffect(fabState) {
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

// 重置定时器的时机
- 用户点击
- 用户拖动
- 记账完成
```

### 动画规格

| 转换 | 动画类型 | 时长 | 缓动 |
|------|----------|------|------|
| 正常→缩小 | 尺寸+透明度 | 200ms | easeOut |
| 缩小→隐藏 | 尺寸+位置+透明度 | 300ms | easeInOut |
| 隐藏→正常 | 尺寸+位置+透明度 | 300ms | spring |
| 拖动吸附 | 位置 | 200ms | spring |

---

## 数据流

```
用户交互
    ↓
FabState 变化
    ↓
LaunchedEffect 检测
    ↓
启动/取消定时器
    ↓
动画执行
    ↓
UI 更新
```

---

## 错误处理

| 场景 | 处理 |
|------|------|
| 图标被拖到屏幕外 | 限制在屏幕边界内 |
| 快速连续点击 | 防抖，300ms 内只响应一次 |
| 屏幕旋转 | 重新计算位置，保持相对位置 |

---

## 测试要点

1. **固定总览卡**
   - 总览卡始终可见
   - 记录列表正常滚动
   - 待确认 Banner 正确显示

2. **可移动加号**
   - 长按拖动正常
   - 吸附到最近边缘
   - 3秒后缩小
   - 5秒后隐藏
   - 点击隐藏状态展开
   - 记账后自动恢复

3. **边界情况**
   - 屏幕旋转
   - 快速操作
   - 无记录状态
