package com.jiyixia.app.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jiyixia.app.JiYiXiaApp
import com.jiyixia.app.data.entity.Category
import com.jiyixia.app.ui.theme.ExpenseRed
import com.jiyixia.app.util.CategoryEmoji
import com.jiyixia.app.util.KeywordMappingManager
import com.jiyixia.app.viewmodel.HomeViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 自定义关键词配置界面
 *
 * 参考一木记账的核心差异化功能：用户可将个人习惯用语绑定至分类，
 * 建立高容错率的录入环境。
 *
 * 例：
 * - "过早"     → "餐饮"（武汉方言，吃早餐）
 * - "打车费"   → "交通"
 * - "猫粮"     → "宠物"
 *
 * 优先级：自定义关键词 > 内置关键词（在 SmartParseUseCase 中体现）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordMappingScreen(
    onNavigateBack: () -> Unit,
    vm: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val context = LocalContext.current
    val app = context.applicationContext as JiYiXiaApp
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by vm.uiState.collectAsState()
    val allCategories = uiState.categories

    // 当前关键词列表
    var mappings by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }

    // 加载关键词
    LaunchedEffect(refreshKey) {
        mappings = KeywordMappingManager.getAllSorted()
    }

    // 添加表单状态
    var newKeyword by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自定义关键词", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // 说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "将你的习惯用语绑定至分类，语音/手动记账时自动识别。\n" +
                    "例：「过早」→「餐饮」、「打车费」→「交通」",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // 添加新关键词区域
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "添加关键词",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))

                    // 关键词输入
                    OutlinedTextField(
                        value = newKeyword,
                        onValueChange = { newKeyword = it },
                        label = { Text("关键词（如：过早）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(Modifier.height(8.dp))

                    // 分类选择
                    Box {
                        OutlinedTextField(
                            value = selectedCategory?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("目标分类") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { categoryMenuExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            trailingIcon = {
                                Text("▼", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        )
                        DropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false }
                        ) {
                            allCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${CategoryEmoji.get(category.name)} ${category.name}")
                                    },
                                    onClick = {
                                        selectedCategory = category
                                        categoryMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // 添加按钮
                    Button(
                        onClick = {
                            if (newKeyword.isBlank()) {
                                scope.launch { snackbarHostState.showSnackbar("请输入关键词") }
                                return@Button
                            }
                            if (selectedCategory == null) {
                                scope.launch { snackbarHostState.showSnackbar("请选择目标分类") }
                                return@Button
                            }
                            val success = KeywordMappingManager.add(newKeyword, selectedCategory!!.name)
                            if (success) {
                                newKeyword = ""
                                selectedCategory = null
                                refreshKey++
                                scope.launch { snackbarHostState.showSnackbar("添加成功") }
                            } else {
                                scope.launch { snackbarHostState.showSnackbar("添加失败") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        enabled = newKeyword.isNotBlank() && selectedCategory != null
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // 现有关键词列表
            Text(
                "已配置的关键词（${mappings.size}）",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            if (mappings.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "暂无自定义关键词\n在上方添加你的第一个关键词",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(mappings) { (keyword, categoryName) ->
                        KeywordItem(
                            keyword = keyword,
                            categoryName = categoryName,
                            onDelete = {
                                KeywordMappingManager.remove(keyword)
                                refreshKey++
                                scope.launch { snackbarHostState.showSnackbar("已删除「$keyword」") }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个关键词项
 */
@Composable
private fun KeywordItem(
    keyword: String,
    categoryName: String,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 关键词
            Text(
                keyword,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(0.4f)
            )
            // 箭头
            Text(
                "→",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            // 分类名
            Row(
                modifier = Modifier.weight(0.5f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    CategoryEmoji.get(categoryName),
                    fontSize = 16.sp
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    categoryName,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            // 删除按钮
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = ExpenseRed.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
