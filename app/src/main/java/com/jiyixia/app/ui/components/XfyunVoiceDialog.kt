package com.jiyixia.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jiyixia.app.util.MscVoiceRecognizer

/**
 * 讯飞语音识别对话框（MSC SDK）
 *
 * 使用讯飞 MSC SDK 进行语音听写，支持在线+离线。
 *
 * @param onResult 识别完成回调
 * @param onDismiss 关闭对话框
 */
@Composable
fun XfyunVoiceDialog(
    onResult: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var statusText by remember { mutableStateOf("正在准备...") }
    var isListening by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("") }
    var hasFinished by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // 开始识别的统一函数
    fun doStart() {
        errorMsg = null
        resultText = ""
        hasFinished = false
        statusText = "请说话..."
        isListening = true
        MscVoiceRecognizer.startListening(
            context = context,
            onResult = { text, isLast ->
                if (text.isNotBlank()) {
                    resultText = text
                    if (isLast) {
                        statusText = "识别完成"
                        hasFinished = true
                        isListening = false
                    } else {
                        statusText = "正在识别..."
                    }
                } else if (isLast) {
                    // 最终结果为空
                    statusText = "没有识别到内容"
                    hasFinished = true
                    isListening = false
                }
            },
            onError = { error ->
                errorMsg = error
                statusText = error
                hasFinished = true
                isListening = false
            }
        )
    }

    DisposableEffect(Unit) {
        doStart()
        onDispose {
            MscVoiceRecognizer.cancel()
        }
    }

    // 麦克风脉冲动画
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Dialog(onDismissRequest = {
        MscVoiceRecognizer.cancel()
        onDismiss()
    }) {
        Card(
            modifier = Modifier.width(280.dp).padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 关闭按钮
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                    IconButton(
                        onClick = {
                            MscVoiceRecognizer.cancel()
                            onDismiss()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, "关闭", Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 麦克风图标 + 脉冲动画
                Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                    if (isListening) {
                        Box(
                            Modifier.size(96.dp).scale(scale).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        )
                    }
                    Box(
                        modifier = Modifier.size(64.dp).clip(CircleShape).background(
                            if (isListening) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Mic, null, Modifier.size(32.dp),
                            tint = if (isListening) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 状态文字
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (errorMsg != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                // 识别结果
                if (resultText.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "「$resultText」",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(20.dp))

                // 操作按钮
                if (hasFinished) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { doStart() },
                            modifier = Modifier.weight(1f)
                        ) { Text("重试", fontSize = 14.sp) }

                        Button(
                            onClick = {
                                MscVoiceRecognizer.cancel()
                                if (resultText.isNotBlank()) onResult(resultText)
                                else onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = resultText.isNotBlank()
                        ) { Text("使用", fontSize = 14.sp) }
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            // 主动停止，触发最终结果
                            MscVoiceRecognizer.stopListening()
                            isListening = false
                            statusText = "正在识别..."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("说完", fontSize = 14.sp) }
                }
            }
        }
    }
}
