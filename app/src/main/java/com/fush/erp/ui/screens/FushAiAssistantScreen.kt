package com.fush.erp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.UserEntity
import com.fush.erp.cloud.FushAiRemoteService
import com.fush.erp.domain.FushAiAssistantService
import com.fush.erp.domain.FushAiConversationTurn
import com.fush.erp.domain.FushAiEngine
import kotlinx.coroutines.launch

private data class FushAiChatMessage(
    val fromUser: Boolean,
    val title: String = "",
    val text: String,
    val engine: FushAiEngine? = null,
    val draftId: Long? = null,
    val draftType: String? = null,
    val draftStatus: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FushAiAssistantScreen(
    container: AppContainer,
    user: UserEntity,
    modifier: Modifier = Modifier,
) {
    val service = remember(container.db, container.cloudSyncRepository) {
        FushAiAssistantService(container.db, FushAiRemoteService(container.cloudSyncRepository))
    }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val messages = remember {
        mutableStateListOf(
            FushAiChatMessage(
                fromUser = false,
                title = "FUSH AI",
                text = "مرحبًا. أقرأ بيانات ERP عبر أدوات محكومة بالصلاحيات، ويمكنني في v211 إنشاء مسودات معزولة لفاتورة مبيعات أو سند خزينة أو أمر إنتاج. المسودة لا تُرحّل ولا تغيّر أي رصيد."
            )
        )
    }
    var question by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var draftBusyId by remember { mutableStateOf<Long?>(null) }

    fun submit(text: String) {
        val clean = text.trim()
        if (clean.isBlank() || loading) return
        val history = messages.map { FushAiConversationTurn(it.fromUser, it.text) }
        messages += FushAiChatMessage(fromUser = true, text = clean)
        question = ""
        loading = true
        scope.launch {
            val answer = runCatching { service.ask(user, clean, history) }
                .getOrElse {
                    com.fush.erp.domain.FushAiAnswer(
                        "تعذر تنفيذ السؤال",
                        "حدث خطأ أثناء قراءة بيانات ERP. لم يتم تعديل أي بيانات. ${it.message.orEmpty()}"
                    )
                }
            messages += FushAiChatMessage(
                fromUser = false, title = answer.title, text = answer.body, engine = answer.engine,
                draftId = answer.draftId, draftType = answer.draftType, draftStatus = answer.draftStatus,
            )
            loading = false
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("FUSH AI — المساعد الذكي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "v211 • قراءة وتحليل + مسودات AI معزولة • لا ترحيل أو تأثير مالي/مخزني من المساعد",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("أسئلة سريعة", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AssistChip(onClick = { submit("ملخص اليوم") }, label = { Text("ملخص اليوم") })
            AssistChip(onClick = { submit("مبيعات اليوم") }, label = { Text("مبيعات اليوم") })
            AssistChip(onClick = { submit("أرصدة الخزينة") }, label = { Text("الخزينة") })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AssistChip(onClick = { submit("الفواتير المتأخرة") }, label = { Text("المتأخرات") })
            AssistChip(onClick = { submit("حالة الإنتاج") }, label = { Text("الإنتاج") })
            AssistChip(onClick = { submit("الشحنات غير المسواة") }, label = { Text("الشحنات") })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AssistChip(onClick = { submit("جهز مسودة فاتورة مبيعات") }, label = { Text("مسودة فاتورة") })
            AssistChip(onClick = { submit("جهز مسودة سند صرف") }, label = { Text("مسودة سند") })
            AssistChip(onClick = { submit("جهز مسودة أمر إنتاج") }, label = { Text("مسودة إنتاج") })
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(if (msg.fromUser) 0.86f else 0.94f),
                        shape = RoundedCornerShape(16.dp),
                        color = if (msg.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            if (msg.title.isNotBlank()) {
                                Text(msg.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                            }
                            msg.engine?.let { engine ->
                                Text(
                                    when (engine) {
                                        FushAiEngine.PRIVATE_LLM -> "نموذج لغوي خاص • أدوات محلية"
                                        FushAiEngine.TOOL_FALLBACK -> "أدوات ERP • صياغة محلية"
                                        FushAiEngine.LOCAL_SAFE -> "الوضع المحلي الآمن"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                            Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                            msg.draftId?.let { draftId ->
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "مسودة #$draftId • ${msg.draftStatus ?: "PENDING"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (msg.draftStatus == "PENDING") {
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            enabled = draftBusyId == null,
                                            onClick = {
                                                draftBusyId = draftId
                                                scope.launch {
                                                    val result = service.approveDraft(user, draftId)
                                                    val index = messages.indexOf(msg)
                                                    if (index >= 0) messages[index] = msg.copy(draftStatus = result.draftStatus ?: "APPROVED")
                                                    messages += FushAiChatMessage(false, result.title, result.body, result.engine, result.draftId, result.draftType, result.draftStatus)
                                                    draftBusyId = null
                                                }
                                            },
                                        ) { Text("اعتماد المسودة") }
                                        androidx.compose.material3.OutlinedButton(
                                            enabled = draftBusyId == null,
                                            onClick = {
                                                draftBusyId = draftId
                                                scope.launch {
                                                    val result = service.cancelDraft(user, draftId)
                                                    val index = messages.indexOf(msg)
                                                    if (index >= 0) messages[index] = msg.copy(draftStatus = result.draftStatus ?: "CANCELLED")
                                                    messages += FushAiChatMessage(false, result.title, result.body, result.engine, result.draftId, result.draftType, result.draftStatus)
                                                    draftBusyId = null
                                                }
                                            },
                                        ) { Text("إلغاء المسودة") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (loading) {
                item {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(22.dp).height(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("أقرأ بيانات FUSH ERP…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                modifier = Modifier.weight(1f),
                label = { Text("اسأل FUSH AI") },
                placeholder = { Text("مثال: جهز مسودة فاتورة للعميل أحمد 10 قطع فوش") },
                maxLines = 3,
                enabled = !loading,
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { submit(question) },
                enabled = question.isNotBlank() && !loading,
            ) {
                Icon(Icons.Filled.Send, contentDescription = "إرسال")
            }
        }
    }
}
