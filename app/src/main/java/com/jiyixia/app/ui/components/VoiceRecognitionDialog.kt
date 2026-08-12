package com.jiyixia.app.ui.components

import android.os.Handler
import android.os.Looper
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
import com.jiyixia.app.util.InAppVoiceRecognizer

/**
 * 应用内语音识别对话框（带自动降级）
 *
 * 策略：
 * 1. 先尝试 SpeechRecognizer（应用内识别，不弹系统授权）
 * 2. 如果引擎不可用或 3 秒内无响应，自动降级到系统语音 Activity
 *    通过 onFallback 回调通知调用方启动 RecognizerIntent
 *
 * 这样在支持 SpeechRecognizer 的设备上用应用内识别，
 * 不支持的设备（如小米的小爱引擎）自动降级到系统 Activity。
 *
 * @param onResult 识别完成回调（text 可能为 null 表示无结果）
 * @param onFallback 降级回调（应用内识别不可用时，调用方应启动系统语音 Activity）
 * @param onDismiss 用户关闭对话框
 */
@Composable
fun VoiceRecognitionDialog(
    onResult: (String?) -> Unit,
    onFallback: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var statusText by remember { mutableStateOf("正在准备...") }
    var isListening by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var hasFinished by remember { mutableStateOf(false) }

    val recognizer = remember { InAppVoiceRecognizer(context) }

    // 降级控制（使用普通变量，避免重组干扰）
    DisposableEffect(Unit) {
        var fallbackTriggered = false
        var readyReceived = false

        fun triggerFallback() {
            if (fallbackTriggered) return
            fallbackTriggered = true
            recognizer.destroy()
            onFallback()
        }

        // 超时检测：3 秒内没有收到 onReadyForSpeech，说明引擎不工作，降级
        val handler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (!readyReceived && !fallbackTriggered) {
                triggerFallback()
            }
        }

        // 尝试应用内语音识别
        recognizer.startListening(
            onReady = {
                readyReceived = true
                statusText = "请说话..."
                isListening = true
            },
            onEnd = {
                statusText = "识别中..."
                isListening = false
            },
            onResult = { text ->
                resultText = text
                hasFinished = true
                statusText = if (text.isNullOrBlank()) "未识别到内容" else "识别完成"
            },
            onError = { error ->
                if (!readyReceived && !fallbackTriggered) {
                    // 引擎未就绪就报错，自动降级到系统语音
                    triggerFallback()
                } else {
                    statusText = error
                    hasFinished = true
                    isListening = false
                }
            }
        )

        // 3 秒超时
        handler.postDelayed(timeoutRunnable, 3000)

        onDispose {
            handler.removeCallbacks(timeoutRunnable)
            recognizer.destroy()
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

    Dialog(onDismissRequest = { onDismiss() }) {
        Card(
            modifier = Modifier
                .width(280.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 关闭按钮（右上角）
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopEnd
                ) {
                    IconButton(
                        onClick = {
                            recognizer.destroy()
                            onDismiss()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 麦克风图标 + 脉冲动画
                Box(
                    modifier = Modifier.size(96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isListening) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .scale(scale)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                if (isListening) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = if (isListening) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 状态文字
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (statusText.contains("失败") || statusText.contains("错误") || statusText.contains("超时"))
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                // 识别结果
                resultText?.let {
                    if (it.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "「$it」",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 操作按钮
                if (hasFinished) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                hasFinished = false
                                resultText = null
                                statusText = "请说话..."
                                isListening = true
                                recognizer.startListening(
                                    onReady = {
                                        statusText = "请说话..."
                                        isListening = true
                                    },
                                    onEnd = {
                                        statusText = "识别中..."
                                        isListening = false
                                    },
                                    onResult = { text ->
                                        resultText = text
                                        hasFinished = true
                                        statusText = if (text.isNullOrBlank()) "未识别到内容" else "识别完成"
                                    },
                                    onError = { error ->
                                        statusText = error
                                        hasFinished = true
                                        isListening = false
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("重试", fontSize = 14.sp)
                        }

                        Button(
                            onClick = {
                                recognizer.destroy()
                                onResult(resultText)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !resultText.isNullOrBlank()
                        ) {
                            Text("使用", fontSize = 14.sp)
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            recognizer.destroy()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("取消", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
