# 记一下 (EasyMoney)

> 轻量记账，支付即记账。让记一笔只需 2 次点击。

一款专为安卓设计的极简记账 App。核心理念：**支付完成 = 记账完成**，零额外操作负担。

## ✨ 核心特性

- 🚀 **2 步记账** — 桌面 Widget / FAB → 输入金额 → 选分类 → 完成
- 🔔 **支付自动识别** — 监听支付宝/微信/银行扣款通知，自动解析金额和商户，本地关键词匹配分类
- ⚠️ **待确认机制** — 低置信度记录标记为"待确认"，通知栏持续提醒，不催你当场改，但不让你忘掉
- 📊 **月度环形图** — 纯 Compose Canvas 手绘，零第三方图表库
- 📤 **CSV 导出** — 一键导出到 Downloads，Excel/WPS 可直接打开
- 🎨 **Material You** — 适配 Android 12+ 动态取色，跟随系统主题自动切换深色/浅色
- 🔒 **纯本地** — 不声明网络权限，数据不离开设备

## 📸 界面预览

| 首页 - 记录列表 | 统计 - 月度概览 | 设置 |
|:---:|:---:|:---:|
| 记录按日期分组，待确认黄色标记 | 收支概览 + 分类环形图 | CSV导出 + 通知权限 |

## 🛠 技术栈

| 类别 | 方案 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 数据库 | Room + SQLite |
| 图表 | Compose Canvas（自绘） |
| 导航 | Navigation Compose |
| 架构 | MVVM（ViewModel + Repository + Room） |
| 异步 | Kotlin Coroutines + Flow |
| Widget | Glance AppWidget |
| 通知监听 | NotificationListenerService（事件驱动） |

## 📁 项目结构

```
app/src/main/java/com/jiyixia/app/
├── JiYiXiaApp.kt              # Application（初始化预设分类）
├── data/
│   ├── entity/Entities.kt     # Room 实体（Record + Category）
│   ├── dao/Daos.kt            # DAO（含分类汇总查询）
│   ├── AppDatabase.kt         # Room 数据库单例
│   └── PresetCategories.kt    # 预设分类数据
├── repository/
│   └── RecordRepository.kt    # 数据仓库
├── viewmodel/
│   ├── ViewModels.kt          # HomeVM + StatsVM
│   └── StatsViewModelFactory.kt
├── ui/
│   ├── MainActivity.kt        # 主 Activity + 底部导航
│   ├── navigation/Screen.kt   # 路由定义
│   ├── screens/
│   │   ├── HomeScreen.kt      # 首页（列表+待确认+记账弹窗）
│   │   ├── StatsScreen.kt     # 统计（概览+环形图）
│   │   └── SettingsScreen.kt  # 设置（导出+通知权限）
│   └── theme/                 # Material 3 主题
├── service/
│   └── PaymentNotificationListener.kt  # 支付通知识别
└── widget/
    └── QuickRecordWidget.kt   # 桌面快捷记账 Widget
```
**环境要求：**
- Android Studio Hedgehog | 2023.1.1+
- JDK 17
- Android SDK 34
- Kotlin 1.9.22

## 📋 迭代计划

- [x] **MVP** — 基础记账 + 月度环形图 + CSV 导出 + Widget + 通知监听
- [ ] **v2** — 自定义分类 + 语音记账 + 年度趋势图 + 应用锁
- [ ] **v3** — 惯性记账建议 + NFC 标签 + 拍照记账 + 预算提醒

## 📄 许可

MIT License
