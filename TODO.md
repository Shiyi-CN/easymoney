# 记一下 - 待办任务清单

> 综合计划：整合原有TODO + 5份架构评审报告
> 按优先级P0→P5执行，每完成一项更新状态并提交版本。

---

## 执行原则

1. 先治已病，再防未病，最后养生
2. 先消除重复，再引入抽象
3. 先有测试，再做迁移
4. 宠物系统推迟到基础稳定后

---

## 🟢 P0 - 立即执行（v1.2.2）✅ 已完成

### A1 删除INTERNET权限
- **状态**：✅ 已完成
- **描述**：删除AndroidManifest.xml中无用的INTERNET权限，减少应用市场审核风险
- **改动范围**：AndroidManifest.xml

### A2 开启exportSchema
- **状态**：✅ 已完成
- **描述**：开启Room的exportSchema = true，配置schema导出路径，为迁移测试提供安全网
- **改动范围**：AppDatabase.kt + build.gradle.kts

### A3 添加测试依赖
- **状态**：✅ 已完成
- **描述**：添加JUnit、MockK、coroutines-test、Room testing等测试依赖
- **改动范围**：build.gradle.kts

### A4 消除三处代码重复
- **状态**：✅ 已完成
- **描述**：统一categoryEmojiMap（HomeScreen、QuickRecordScreen、BubbleInputActivity三处重复），创建CategoryEmoji工具类
- **改动范围**：CategoryEmoji.kt（新建）+ HomeScreen.kt + QuickRecordScreen.kt + BubbleInputActivity.kt
- **成果**：删除约300行重复代码

### A5 为VoiceCategorizer写回归测试
- **状态**：✅ 已完成
- **描述**：为VoiceCategorizer编写30+回归测试用例，覆盖所有输入格式
- **改动范围**：VoiceCategorizerTest.kt（新建）

---

## 🟡 P1 - v1.3.0开发前

### B1 Double→Long迁移（最高优先级）
- **状态**：✅ 已完成
- **描述**：将Record.amount从Double改为Long（单位：分），解决浮点精度问题
- **改动范围**：Entities.kt + AppDatabase.kt（迁移脚本v4→v5）+ AmountExt.kt（新建）+ Daos.kt + RecordRepository.kt + ViewModels.kt + HomeScreen.kt + StatsScreen.kt + EditRecordDialog.kt + QuickRecordScreen.kt + BubbleInputActivity.kt + PaymentNotificationListener.kt
- **验收标准**：金额计算精度完全正确，迁移脚本通过测试
- **完成时间**：2026-06-09

### B2 添加金额扩展函数
- **状态**：✅ 已完成
- **描述**：创建AmountExt.kt，统一金额转换逻辑（Long→String、String→Long）
- **改动范围**：AmountExt.kt（新建）
- **完成时间**：2026-06-09

### B3 添加ProGuard/R8规则
- **状态**：✅ 已完成
- **描述**：添加Room Entity、DAO、Compose的keep规则，防止Release构建崩溃
- **改动范围**：proguard-rules.pro
- **完成时间**：2026-06-12

### B4 重构ViewModel的combine模式
- **状态**：✅ 已完成
- **描述**：将ViewModel中的`@Suppress("UNCHECKED_CAST")`模式改为data class包装，提升类型安全
- **改动范围**：ViewModels.kt
- **完成时间**：2026-06-12

### B5 Repository层确保IO调度
- **状态**：✅ 已完成
- **描述**：确保Repository中所有DAO调用都在Dispatchers.IO上，防止主线程写库
- **改动范围**：RecordRepository.kt
- **完成时间**：2026-06-12

### B6 版本号管理规范
- **状态**：✅ 已完成
- **描述**：建立版本管理流程，每次功能/修复完成同步升级版本号
- **改动范围**：build.gradle.kts + CHANGELOG.md
- **验收标准**：每次提交前检查 versionCode/versionName 是否已更新
- **完成时间**：2026-06-12

---

## 🟢 P2 - v1.3.0开发中

### C1 提取SmartParseUseCase
- **状态**：✅ 已完成
- **描述**：将VoiceCategorizer重命名为SmartParseUseCase并移入domain/目录，三个入口统一调用
- **改动范围**：新建domain/usecase/SmartParseUseCase.kt + 修改所有调用方
- **完成时间**：2026-06-13

### C2 添加输入验证层
- **状态**：✅ 已完成
- **描述**：在UseCase层添加输入验证（防止负数、极大值、超长备注）
- **改动范围**：新建InputValidationUseCase.kt + 修改ViewModels.kt
- **完成时间**：2026-06-12

### C3 全局错误处理
- **状态**：✅ 已完成
- **描述**：ViewModel层统一捕获异常，向用户展示友好错误提示（Snackbar）
- **改动范围**：ViewModels.kt
- **完成时间**：2026-06-12

### C4 deleteAll()安全化
- **状态**：✅ 已完成
- **描述**：移除DAO层的deleteAll()，改为Repository层的safeDeleteAll()方法，先自动备份再删除
- **改动范围**：RecordDao.kt + RecordRepository.kt + SettingsScreen.kt
- **完成时间**：2026-06-12

### #1 版本号管理规范
- **状态**：待开始（合并到B6）

### #4 快速记账模式开关
- **状态**：✅ 已完成
- **描述**：设置页新增「记账模式」开关，支持极速模式（500ms自动保存）和确认模式（手动点按钮保存）
- **改动范围**：SettingsScreen.kt + ThemePreferences.kt
- **验收标准**：两种模式可切换，偏好持久化
- **完成时间**：2026-06-12

### #5 截屏保护
- **状态**：✅ 已完成
- **描述**：FLAG_SECURE 防止截屏录屏泄露财务数据，设置页开关控制
- **改动范围**：MainActivity.kt + SettingsScreen.kt + ThemePreferences.kt
- **验收标准**：开关控制截屏保护，关闭后允许截屏
- **完成时间**：2026-06-12

### #7 左滑删除记录（含撤销）
- **状态**：✅ 已完成
- **描述**：首页记录列表用SwipeToDismiss实现左滑删除，支持撤销（Snackbar + 延迟真正删除）
- **改动范围**：HomeScreen.kt
- **验收标准**：左滑出现删除操作，支持撤销，交互流畅
- **完成时间**：2026-06-12

### #8 识别日志脱敏
- **状态**：✅ 已完成
- **描述**：设置页识别日志只显示时间+分类+金额，不暴露原始通知全文
- **改动范围**：PaymentNotificationListener.kt
- **验收标准**：日志不包含完整通知文本
- **完成时间**：2026-06-12

### #9 自动确认规则
- **状态**：✅ 已完成
- **描述**：设置页「自动确认规则」功能实现，置信度阈值可调（默认80%）
- **改动范围**：SettingsScreen.kt + ThemePreferences.kt
- **验收标准**：阈值可配置，超过阈值的记录自动确认
- **完成时间**：2026-06-12

### v1.3.2 报销逻辑优化 + 记账体验改进
- **状态**：✅ 已完成
- **描述**：
  - 撤销报销时自动删除关联收入记录
  - 已报销记录新增撤销报销按钮
  - 报销到账记录备注格式优化（[已报销] 前缀）
  - 报销到账记录浅绿色背景
  - 修复记账页面无法切换收入/支出
  - 记录后自动切换类型（页面内连续记账）
  - 智能默认优化（默认支出，记住分类）
- **改动范围**：ViewModels.kt + HomeScreen.kt + QuickRecordScreen.kt + ReimbursableRecordsScreen.kt
- **完成时间**：2026-06-14

---

## 🔵 P3 - v1.4.0

### D1 小米通知监听修复
- **状态**：✅ 已完成
- **描述**：精简3+1层方案：onListenerDisconnected+requestRebind → Foreground Service保活 → 权限引导UI → 状态检测页面
- **改动范围**：PaymentNotificationListener.kt + KeepAliveService.kt（新建）+ AndroidManifest.xml
- **完成时间**：2026-06-14

### D2 备份文件加密
- **状态**：✅ 已完成
- **描述**：对备份文件做 AES-256-GCM 加密保护，防止备份文件被直接读取
- **改动范围**：CryptoUtil.kt（新建）+ BackupUtil.kt + SettingsScreen.kt
- **完成时间**：2026-06-14

### D3 添加崩溃日志
- **状态**：✅ 已完成
- **描述**：自建 CrashHandler 捕获全局异常，崩溃日志存本地 filesDir/crash/
- **改动范围**：CrashHandler.kt（新建）+ CrashLogScreen.kt（新建）+ JiYiXiaApp.kt + SettingsScreen.kt + MainActivity.kt + AndroidManifest.xml
- **完成时间**：2026-06-14

### D4 通知去重改进
- **状态**：✅ 已完成
- **描述**：去重键改为基于金额+时间窗口，同一分钟内相同金额的通知只处理一次
- **改动范围**：PaymentNotificationListener.kt
- **完成时间**：2026-06-14

### D5 连续记账天数徽章
- **状态**：✅ 已完成
- **描述**：极轻量游戏化验证，只做连续天数徽章（纯数据+UI展示），验证用户是否在意游戏化反馈
- **改动范围**：Daos.kt + RecordRepository.kt + ViewModels.kt + HomeScreen.kt
- **完成时间**：2026-06-14

### D6 检查更新功能
- **状态**：✅ 已完成
- **描述**：设置页点击版本号可检查GitHub最新版本，显示更新日志并提供下载链接
- **改动范围**：UpdateChecker.kt（新建）+ SettingsScreen.kt + AndroidManifest.xml
- **完成时间**：2026-06-14

### #6 月度趋势图
- **状态**：✅ 已完成
- **描述**：StatsScreen的「月度趋势」Tab用Canvas绘制近6个月收支柱状图
- **改动范围**：StatsScreen.kt + StatsViewModel.kt + RecordRepository.kt
- **完成时间**：2026-06-14

### #10 年度总览
- **状态**：✅ 已完成
- **描述**：新增年度汇总页：12个月收支柱状图 + 年度结余 + Top分类
- **改动范围**：YearStatsScreen.kt（新建）+ StatsScreen.kt + MainActivity.kt + Screen.kt
- **完成时间**：2026-06-14

### #13 搜索/筛选记录
- **状态**：✅ 已完成
- **描述**：首页新增搜索入口，支持按关键词筛选
- **改动范围**：Daos.kt + RecordRepository.kt + ViewModels.kt + HomeScreen.kt
- **完成时间**：2026-06-14

---

## ⚪ P4 - v1.5.0+（视D5验证结果决定）

### E1 DomainEvents事件总线
- **状态**：待开始
- **描述**：如果D5连续记账徽章验证有效，引入SharedFlow实现的领域事件总线
- **改动范围**：新建domain/event/DomainEvents.kt
- **预计时间**：2天

### #15 侧边栏磁贴自动隐藏
- **状态**：进行中
- **描述**：悬浮气泡5秒无操作后自动隐藏成边缘小条，轻触呼出
- **改动范围**：BubbleService.kt + SettingsScreen.kt
- **验收标准**：
  - 气泡5秒无操作后自动隐藏成边缘小条（8x40px）
  - 轻触边缘小条可呼出气泡
  - 隐藏/呼出动画流畅（0.3秒）
  - 设置页可调整隐藏时间（3-10秒）
  - 设置页可关闭自动隐藏功能

### #16 桌面宠物基础版
- **状态**：待开始
- **描述**：Q版可爱小动物，记账后有即时反馈
- **改动范围**：新增PetService.kt + PetView.kt + PetState.kt
- **验收标准**：
  - 宠物正常显示在屏幕上（可拖动）
  - 记账后宠物有即时反馈（跳一下+文字提示）
  - 宠物可点击触发记账
  - 设置页可控制宠物显示/隐藏
  - 动画帧率控制在15fps以节省电量
- **预计时间**：3-5天
- **依赖**：#15完成后

### #17 桌面宠物情绪系统
- **状态**：待开始
- **描述**：宠物根据用户记账行为展现不同情绪
- **改动范围**：PetState.kt + PetAnimations.kt
- **验收标准**：
  - 今天已记账 → 宠物开心（摇尾巴、眨眼）
  - 昨天记账了今天还没 → 宠物普通（正常站立）
  - 连续2天没记账 → 宠物困倦（打哈欠、眯眼）
  - 连续3天+没记账 → 宠物难过（低头、眼泪）
  - 情绪变化有平滑过渡动画
- **预计时间**：2-3天
- **依赖**：#16完成后

### #18 桌面宠物成长系统
- **状态**：待开始
- **描述**：宠物随记账笔数成长，解锁新形态和装扮
- **改动范围**：PetState.kt + PetAnimations.kt + DataStore
- **验收标准**：
  - 🐣 幼年（0-30笔）：小小圆圆，基础动作
  - 🐥 少年（31-100笔）：稍大有小翅膀，解锁跳舞动作
  - 🐔 成年（101-300笔）：完全体有装饰，更多表情
  - 🦅 进阶（300笔+）：特殊形态，隐藏装扮
  - 成长动画过渡自然
- **预计时间**：2-3天
- **依赖**：#17完成后

### #14 兼容其他记账APP导入
- **状态**：待开始
- **描述**：支持从随手记、钱迹等主流记账APP导入数据，降低用户迁移成本
- **改动范围**：新增ImportUtil + ImportScreen + 分类映射逻辑
- **验收标准**：
  - 支持CSV文件导入（随手记、钱迹格式）
  - 自动识别文件格式并解析
  - 分类智能映射（"吃饭/外卖" → 「餐饮」）
  - 导入前预览数据（条数、时间范围、分类映射结果）
  - 支持导入自定义分类
  - 冲突处理：跳过重复记录
  - 未匹配分类自动归入「其他」
  - 导入后支持批量修改分类（按原分类名筛选）
  - 导入完成后显示统计（成功/失败/跳过条数）
- **优先级**：随手记 > 钱迹（用户量优先）
- **格式**：仅支持CSV（不引入Excel/JSON依赖）

### E7 引入Navigation Compose
- **状态**：待开始
- **描述**：引入Navigation Compose统一页面导航
- **改动范围**：新增导航图 + 修改所有页面跳转
- **预计时间**：1天

---

## 🟤 P5 - v2.0.0 安全大版本

### #11 应用锁（指纹/面容/PIN）
- **状态**：待开始
- **描述**：启动App时验证身份，使用BiometricPrompt API，设置页开关+备用PIN
- **改动范围**：新增应用锁模块 + MainActivity + SettingsScreen
- **验收标准**：支持指纹/面容/PIN三种方式，可开关

### #12 数据库加密（SQLCipher）
- **状态**：待开始
- **描述**：引入SQLCipher加密数据库，密钥存AndroidKeystore；同时整改CSV导出和备份路径
- **改动范围**：AppDatabase + 依赖引入 + BackupUtil + SettingsScreen + 数据迁移
- **验收标准**：数据库加密存储，所有文件路径在App目录内

### E3 引入DI框架（Koin）
- **状态**：待开始
- **描述**：当团队扩张到2-3人时，引入Koin轻量级DI框架
- **改动范围**：新增di模块 + 修改所有依赖注入
- **预计时间**：1天

### E4 配置CI/CD（GitHub Actions）
- **状态**：待开始
- **描述**：配置GitHub Actions CI流水线（Lint + Test + Build）
- **改动范围**：.github/workflows/
- **预计时间**：1-2天

---

## 版本规划

| 版本 | 包含任务 | 状态 | 预计时间 |
|------|----------|------|----------|
| v1.2.2 | P0全部（A1-A5） | ✅ 已完成 | - |
| v1.3.0 | P1（B1-B6）+ P2（C1-C9） | ✅ P1已完成 | 2-3周 |
| v1.4.0 | P3（D1-D8） | 待开始 | 2周 |
| v1.5.0 | P4（E1-E7，视D5验证结果） | 待开始 | 3-4周 |
| v2.0.0 | P5（F1-F4） | 待开始 | 2周 |

---

## 规则

1. 每完成一个任务 → 更新本文件状态 → 更新版本号 → 提交+tag → 推送GitHub → 构建APK
2. APK 命名格式：`记一下_v{版本号}.apk`
3. CHANGELOG 同步更新

---

*最后更新：2026-06-13*
