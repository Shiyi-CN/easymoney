# 记一下 (EasyMoney)

> 轻量记账，支付即记账。让记一笔只需 2 次点击。

一款专为安卓设计的极简记账 App。核心理念：**支付完成 = 记账完成**，零额外操作负担。

## ✨ 核心特性

- 🚀 **2 步记账** — 桌面 Widget / FAB → 输入金额 → 选分类 → 完成
- 🎤 **语音记账** — 说"午餐 38"自动填金额+分类，支持"打车 25 块 5"等多种口语
- 🧾 **报销管理** — 标记可报销→已报销，关联报销对象（XX公司/XX人），统计页待报销汇总
- 🔔 **支付自动识别** — 监听支付宝/微信/银行/云闪付通知，自动解析金额和商户，支持收入识别
- ⚠️ **待确认机制** — 低置信度记录标记为"待确认"，通知栏持续提醒
- 📊 **月度环形图** — 纯 Compose Canvas 手绘，零第三方图表库
- 📤 **CSV 导出** — 一键导出到 Downloads
- 🎨 **Material You** — 适配 Android 12+ 动态取色
- 🔒 **纯本地优先** — 语音识别优先离线，数据不离开设备

## 🛠 技术栈

| 类别 | 方案 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 数据库 | Room + SQLite (v4 migration) |
| 图表 | Compose Canvas（自绘） |
| 导航 | Navigation Compose |
| 架构 | MVVM（ViewModel + Repository） |
| 语音 | Android SpeechRecognizer（优先离线） |
| Widget | Glance AppWidget |
| 通知监听 | NotificationListenerService |

## 📋 迭代进度

- [x] **v1.0** — 基础记账 + 月度环形图 + CSV 导出 + Widget + 通知监听
- [x] **v1.1** — 语音记账 + 报销管理（待/已报销 + 报销对象）+ 报销统计 + 新图标
- [ ] **v1.2** — 自定义分类 + 年度趋势图 + 应用锁 + 桌面宠物/植物 Widget 🌱
- [ ] **v2.0** — 惯性记账建议 + NFC 标签 + 拍照记账 + 预算提醒

## 📄 许可

MIT License
