package com.jiyixia.app.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jiyixia.app.data.entity.Category
import com.jiyixia.app.data.entity.Record
import com.jiyixia.app.ui.theme.*
import com.jiyixia.app.util.VoiceCategorizer
import com.jiyixia.app.viewmodel.HomeViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 导入 HomeViewModelFactory
import com.jiyixia.app.ui.screens.HomeViewModelFactory

/**
 * 快速记账界面（全屏）
 *
 * 设计理念：
 * 1. 2步完成记账：输入金额 → 选择分类
 * 2. 自动保存：输入金额后延迟自动保存
 * 3. 记完即退：保存后自动返回
 * 4. 智能默认：记住上次使用的分类
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickRecordScreen(
    onNavigateBack: () -> Unit,
    vm: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val uiState by vm.uiState.collectAsState()
    val selectedType by vm.selectedType.collectAsState()

    // 记账状态
    var amountText by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(0L) }
    var currentType by remember { mutableStateOf(selectedType) }
    var isSaving by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }

    // 协程作用域（修复：使用 Job 来取消之前的协程）
    val scope = rememberCoroutineScope()
    var saveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // 按当前类型筛选分类（修复：使用 uiState.categories 作为 key）
    val displayCategories = remember(currentType, uiState.categories) {
        if (currentType == 0) {
            uiState.categories.filter { it.type == 0 }
        } else {
            uiState.categories.filter { it.type == 1 }
        }
    }

    // 智能默认：记住上次使用的分类（优化：更智能的默认值逻辑）
    LaunchedEffect(uiState.records, displayCategories) {
        if (displayCategories.isEmpty()) return@LaunchedEffect

        // 优先级1：使用上次记录的分类
        val lastRecord = uiState.records.firstOrNull()
        if (lastRecord != null && displayCategories.any { it.id == lastRecord.categoryId }) {
            selectedCategoryId = lastRecord.categoryId
            currentType = lastRecord.type
            return@LaunchedEffect
        }

        // 优先级2：使用最常用的分类
        val categoryFrequency = uiState.records
            .groupBy { it.categoryId }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }

        if (categoryFrequency.isNotEmpty()) {
            val mostUsedCategoryId = categoryFrequency.first().first
            if (displayCategories.any { it.id == mostUsedCategoryId }) {
                selectedCategoryId = mostUsedCategoryId
                return@LaunchedEffect
            }
        }

        // 优先级3：使用第一个分类
        selectedCategoryId = displayCategories.first().id
    }

    // 自动保存逻辑（修复：使用 Job 来取消之前的协程，避免并发问题）
    LaunchedEffect(amountText) {
        // 取消之前的保存任务
        saveJob?.cancel()

        // 等待一小段时间，确保状态更新
        delay(100)

        if (amountText.isNotBlank()) {
            // 使用 VoiceCategorizer 统一解析逻辑
            val nameToId = displayCategories.associate { it.name to it.id }
            val defaultCategoryId = displayCategories.firstOrNull()?.id ?: 0L
            val parsed = VoiceCategorizer.parse(
                text = amountText,
                categoryNameToId = nameToId,
                defaultCategoryId = defaultCategoryId
            )

            if (parsed != null && parsed.amount > 0) {
                // 启动新的保存任务
                saveJob = scope.launch {
                    // 延迟500ms后自动保存（给用户修正时间）
                    delay(500)

                    // 再次解析，确保用户没有修改
                    val currentParsed = VoiceCategorizer.parse(
                        text = amountText,
                        categoryNameToId = nameToId,
                        defaultCategoryId = defaultCategoryId
                    )

                    if (currentParsed != null && currentParsed.amount > 0) {
                        isSaving = true
                        val category = displayCategories.find { it.id == currentParsed.categoryId }
                            ?: displayCategories.find { it.id == selectedCategoryId }

                        if (category != null) {
                            vm.addRecord(
                                amount = currentParsed.amount,
                                categoryId = category.id,
                                note = currentParsed.note,  // 使用 VoiceCategorizer 处理后的备注
                                type = if (currentParsed.isExpense) 0 else 1,
                                isReimbursable = currentParsed.isReimbursable,
                                reimbursementTarget = currentParsed.reimbursementTarget
                            )

                            // 显示成功提示
                            showSuccess = true
                            delay(1000) // 显示1秒

                            // 返回上一页
                            onNavigateBack()
                        }
                        isSaving = false
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("快速记账", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 金额输入区域
            AmountInputSection(
                amountText = amountText,
                onAmountChange = { amountText = it },
                currentType = currentType,
                onTypeChange = { currentType = it }
            )

            Spacer(Modifier.height(24.dp))

            // 分类选择区域
            CategorySelectionSection(
                categories = displayCategories,
                selectedCategoryId = selectedCategoryId,
                onCategorySelect = { selectedCategoryId = it }
            )

            Spacer(Modifier.weight(1f))

            // 状态提示
            if (isSaving) {
                Text(
                    "保存中...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (showSuccess) {
                Text(
                    "✓ 已保存",
                    color = IncomeGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

/**
 * 金额输入区域
 */
@Composable
private fun AmountInputSection(
    amountText: String,
    onAmountChange: (String) -> Unit,
    currentType: Int,
    onTypeChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        // 收支切换
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Surface2)
                .padding(3.dp)
        ) {
            listOf(0 to "支出", 1 to "收入").forEach { (type, label) ->
                val isSelected = currentType == type
                val bg = when {
                    !isSelected -> Color.Transparent
                    type == 0 -> ExpenseRed
                    else -> IncomeGreen
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTypeChange(type) }
                        .clip(RoundedCornerShape(9.dp))
                        .background(bg)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 金额输入（允许输入中文和数字，用于文本输入自动分类）
        OutlinedTextField(
            value = amountText,
            onValueChange = { v ->
                if (v.length <= 20)  // 允许输入更长的文本
                    onAmountChange(v)
            },
            label = { Text("金额或描述（如：早餐10元）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),  // 改为文本键盘
            textStyle = LocalTextStyle.current.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (currentType == 0) ExpenseRed else IncomeGreen
            )
        )
    }
}

/**
 * 分类选择区域（支持滚动）
 */
@Composable
private fun CategorySelectionSection(
    categories: List<Category>,
    selectedCategoryId: Long,
    onCategorySelect: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "选择分类",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(10.dp))

        // 分类网格（支持滚动）
        val cols = 4
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 200.dp)  // 限制最大高度
        ) {
            items(categories.size) { idx ->
                val cat = categories[idx]
                val isSelected = selectedCategoryId == cat.id
                Box(
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .clickable { onCategorySelect(cat.id) }
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else Surface2
                        )
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(categoryEmoji(cat.name), fontSize = 20.sp)
                        Text(
                            cat.name,
                            fontSize = 11.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// 分类 Emoji 映射
private val categoryEmojiMap = mapOf(
    // 支出分类
    "餐饮" to "🍜", "交通" to "🚇", "购物" to "🛒", "居住" to "🏠",
    "娱乐" to "🎮", "医疗" to "🏥", "教育" to "📚", "通讯" to "📱",
    "社交" to "🤝", "美容" to "💄", "宠物" to "🐱", "办公" to "💼",
    "维修" to "🔧", "捐赠" to "❤️", "其他" to "📋",
    // 收入分类
    "工资" to "💰", "奖金" to "🏆", "理财" to "📈", "兼职" to "💼",
    "红包" to "🧧", "报销" to "🧾", "租金" to "🏠", "退款" to "↩️",
    "中奖" to "🎰"
)

private fun categoryEmoji(name: String?) = categoryEmojiMap[name] ?: "📋"

/**
 * 从输入文本中提取金额
 * 支持格式：
 * - 纯数字：38、38.5、38.50
 * - 带单位：38元、38.5元
 * - 文本+金额：早餐38元、打车25块5
 * - 中文数字：两元、四十元、二百元
 * - 数字+万/千：1万、2千、1.5万
 */
private fun extractAmount(text: String): Double? {
    // 1. 尝试直接解析为数字
    val directAmount = text.toDoubleOrNull()
    if (directAmount != null && directAmount > 0) {
        return directAmount
    }

    // 2. 尝试匹配 "数字元" 格式
    val yuanPattern = Regex("""(\d+\.?\d*)\s*元""")
    val yuanMatch = yuanPattern.find(text)
    if (yuanMatch != null) {
        val amount = yuanMatch.groupValues[1].toDoubleOrNull()
        if (amount != null && amount > 0) {
            return amount
        }
    }

    // 3. 尝试匹配 "数字块数字" 格式（如：25块5）
    val kuaiPattern = Regex("""(\d+)\s*块\s*(\d+)""")
    val kuaiMatch = kuaiPattern.find(text)
    if (kuaiMatch != null) {
        val main = kuaiMatch.groupValues[1].toDoubleOrNull()
        val frac = kuaiMatch.groupValues[2].toDoubleOrNull()
        if (main != null && frac != null) {
            val fracAdjusted = if (frac >= 10) frac / 100.0 else frac / 10.0
            return main + fracAdjusted
        }
    }

    // 4. 尝试匹配 "数字万" 格式（如：1万、1.5万）
    val wanPattern = Regex("""(\d+\.?\d*)\s*万""")
    val wanMatch = wanPattern.find(text)
    if (wanMatch != null) {
        val amount = wanMatch.groupValues[1].toDoubleOrNull()
        if (amount != null && amount > 0) {
            return amount * 10000
        }
    }

    // 5. 尝试匹配 "数字千" 格式（如：2千、1.5千）
    val qianPattern = Regex("""(\d+\.?\d*)\s*千""")
    val qianMatch = qianPattern.find(text)
    if (qianMatch != null) {
        val amount = qianMatch.groupValues[1].toDoubleOrNull()
        if (amount != null && amount > 0) {
            return amount * 1000
        }
    }

    // 6. 尝试匹配中文数字（如：两元、四十元、二百元）
    val chineseAmount = extractChineseAmount(text)
    if (chineseAmount != null && chineseAmount > 0) {
        return chineseAmount
    }

    // 7. 尝试匹配任意数字
    val numberPattern = Regex("""(\d+\.?\d*)""")
    val numberMatch = numberPattern.find(text)
    if (numberMatch != null) {
        val amount = numberMatch.groupValues[1].toDoubleOrNull()
        if (amount != null && amount > 0) {
            return amount
        }
    }

    return null
}

/**
 * 从输入文本中提取中文数字金额
 * 支持格式：
 * - 两元、三元、四元...
 * - 四十元、五十元...
 * - 二百元、三百元...
 * - 两千三百五十元
 * - 六千（不带元）
 * - 两千三百五十（不带元）
 */
private fun extractChineseAmount(text: String): Double? {
    // 尝试匹配中文数字+元格式
    val chinesePattern = Regex("""([零一二两三四五六七八九十百千万]+)\s*元""")
    val chineseMatch = chinesePattern.find(text)
    if (chineseMatch != null) {
        val chineseNum = chineseMatch.groupValues[1]
        val amount = chineseToArabic(chineseNum)
        if (amount != null && amount > 0) {
            return amount.toDouble()
        }
    }

    // 尝试匹配单独的中文数字（不带元）
    val standalonePattern = Regex("""([零一二两三四五六七八九十百千万]+)""")
    val standaloneMatches = standalonePattern.findAll(text)
    for (match in standaloneMatches) {
        val chineseNum = match.groupValues[1]
        // 过滤掉太短的匹配（可能是分类关键词）
        if (chineseNum.length >= 2) {
            val amount = chineseToArabic(chineseNum)
            if (amount != null && amount > 0) {
                return amount.toDouble()
            }
        }
    }

    return null
}

/**
 * 中文数字转换为阿拉伯数字
 * 支持：三千五百二十一 → 3521
 */
private fun chineseToArabic(chinese: String): Long? {
    if (chinese.isEmpty()) return null

    val digitMap = mapOf(
        '零' to 0, '一' to 1, '二' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        '两' to 2
    )
    val unitMap = mapOf('十' to 10L, '百' to 100L, '千' to 1000L, '万' to 10000L)

    var result = 0L
    var temp = 0L

    for (ch in chinese) {
        when {
            ch in digitMap -> {
                temp += digitMap[ch]!!.toLong()
            }
            ch in unitMap -> {
                val unit = unitMap[ch]!!
                if (temp == 0L) temp = 1L  // "十" = 10
                if (ch == '万') {
                    result += temp * unit
                    temp = 0
                } else {
                    temp *= unit
                }
            }
        }
    }

    result += temp
    return if (result > 0) result else null
}

/**
 * 从输入文本中提取分类
 * 支持关键词匹配分类
 */
private fun extractCategory(text: String, categories: List<Category>): Category? {
    // 分类关键词映射
    val categoryKeywords = mapOf(
        "餐饮" to listOf("早餐", "午餐", "晚餐", "吃饭", "外卖", "餐厅", "火锅", "奶茶", "咖啡", "小吃", "零食", "饮料", "水果", "蔬菜", "肉", "鱼", "米饭", "面条", "面包", "蛋糕"),
        "交通" to listOf("打车", "地铁", "公交", "加油", "停车", "高铁", "火车", "机票", "飞机", "出租车", "网约车", "共享单车", "骑行"),
        "购物" to listOf("超市", "淘宝", "京东", "购物", "衣服", "鞋子", "包包", "数码", "手机", "电脑", "家电", "家具", "化妆品", "护肤品", "书籍"),
        "居住" to listOf("房租", "房贷", "水电", "燃气", "物业", "网费", "话费", "维修", "装修", "家具", "家电"),
        "娱乐" to listOf("电影", "游戏", "KTV", "旅游", "景点", "门票", "运动", "健身", "瑜伽", "游泳", "剧本杀", "密室", "游乐园"),
        "医疗" to listOf("医院", "药店", "挂号", "药", "看病", "门诊", "体检", "保险", "牙科", "眼科", "皮肤科", "中医", "按摩", "理疗"),
        "教育" to listOf("书", "课程", "培训", "考试", "学费", "教材", "文具", "网课", "辅导班", "兴趣班"),
        "通讯" to listOf("话费", "流量", "充值", "套餐", "宽带"),
        "社交" to listOf("人情", "礼物", "请客", "红包", "份子钱", "聚餐", "送礼", "慰问"),
        "美容" to listOf("护肤品", "理发", "化妆品", "美甲", "美发", "美容", "SPA", "按摩"),
        "宠物" to listOf("猫粮", "狗粮", "宠物", "猫", "狗", "宠物食品", "宠物医疗", "宠物美容"),
        "办公" to listOf("文具", "打印", "复印", "办公用品", "设备", "软件", "买", "购买", "公司", "办公"),
        "维修" to listOf("维修", "修理", "手机维修", "电脑维修", "家电维修", "汽车维修"),
        "捐赠" to listOf("捐款", "慈善", "公益", "爱心")
    )

    // 收入分类关键词
    val incomeKeywords = mapOf(
        "工资" to listOf("工资", "薪水", "月薪", "基本工资", "到手"),
        "奖金" to listOf("奖金", "年终奖", "项目奖", "绩效", "奖励"),
        "理财" to listOf("理财", "利息", "基金", "股票", "收益", "存款", "余额宝"),
        "兼职" to listOf("兼职", "副业", "外快", "稿费", "接单"),
        "红包" to listOf("红包", "收红包", "零花钱", "压岁钱", "份子钱"),
        "报销" to listOf("报销", "公司报销", "差旅报销"),
        "租金" to listOf("租金", "房租收入", "设备租赁"),
        "退款" to listOf("退款", "商品退款", "服务退款"),
        "中奖" to listOf("中奖", "彩票", "抽奖")
    )

    // 优先级1：尝试匹配收入分类（因为收入分类更明确）
    for ((categoryName, keywords) in incomeKeywords) {
        for (keyword in keywords) {
            if (text.contains(keyword)) {
                val category = categories.find { it.name == categoryName && it.type == 1 }
                if (category != null) {
                    return category
                }
            }
        }
    }

    // 优先级2：尝试匹配支出分类（使用更智能的上下文判断）
    // 如果包含"公司"、"办公"等关键词，优先分类到"办公"
    val officeKeywords = listOf("公司", "办公", "工作", "单位")
    val hasOfficeContext = officeKeywords.any { text.contains(it) }

    if (hasOfficeContext) {
        // 如果有办公上下文，优先匹配办公分类
        val officeCategory = categories.find { it.name == "办公" && it.type == 0 }
        if (officeCategory != null) {
            return officeCategory
        }
    }

    // 优先级3：尝试匹配其他支出分类
    for ((categoryName, keywords) in categoryKeywords) {
        // 跳过办公分类（已经优先匹配过了）
        if (categoryName == "办公") continue

        for (keyword in keywords) {
            if (text.contains(keyword)) {
                val category = categories.find { it.name == categoryName && it.type == 0 }
                if (category != null) {
                    return category
                }
            }
        }
    }

    return null
}

/**
 * 检查是否包含报销关键词
 */
private fun checkReimbursement(text: String): Boolean {
    val reimbursementKeywords = listOf("报销", "公司", "单位", "部门", "差旅")
    return reimbursementKeywords.any { text.contains(it) }
}

/**
 * 从输入文本中提取报销对象
 * 支持格式：
 * - "打车到公司20元" → "公司"
 * - "腾讯公司报销50元" → "腾讯公司"
 * - "川南分公司机票50元" → "川南分公司"
 * - "机票川南分公司50元" → "川南分公司"
 * - "差旅报销100元" → "差旅"
 */
private fun extractReimbursementTarget(text: String): String {
    // 报销对象关键词
    val targetKeywords = listOf(
        "公司", "单位", "部门", "差旅", "客户", "供应商", "合作伙伴"
    )

    // 优先级1：尝试匹配 "XX公司" 格式（如"川南分公司"）
    // 使用更智能的方式：找到"公司"关键词，然后提取前面的完整词组
    for (keyword in targetKeywords) {
        val keywordIndex = text.indexOf(keyword)
        if (keywordIndex > 0) {
            // 提取关键词前面的字符
            val beforeKeyword = text.substring(0, keywordIndex)

            // 尝试提取完整的公司名称（2-8个中文字符）
            // 优先匹配较长的公司名称
            val companyNamePattern = Regex("""([一-龥]{2,8})$""")
            val companyNameMatch = companyNamePattern.find(beforeKeyword)
            if (companyNameMatch != null) {
                val companyName = companyNameMatch.groupValues[1].trim()
                if (companyName.isNotEmpty() && !companyName.matches(Regex("""\d+"""))) {
                    return "${companyName}${keyword}"
                }
            }

            // 如果没有匹配到完整公司名称，返回关键词本身
            return keyword
        }
    }

    // 优先级2：尝试匹配 "到XX公司" 格式（如"到公司"）
    val toCompanyPattern = Regex("""到([一-龥]{2,8})(?:公司|单位|部门)""")
    val toCompanyMatch = toCompanyPattern.find(text)
    if (toCompanyMatch != null) {
        val target = toCompanyMatch.groupValues[1].trim()
        if (target.isNotEmpty() && !target.matches(Regex("""\d+"""))) {
            return "${target}${if (text.contains("公司")) "公司" else if (text.contains("单位")) "单位" else "部门"}"
        }
    }

    return ""
}
