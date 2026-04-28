package dev.agentone.ui.pages.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.agentone.AgentOneApp
import dev.agentone.core.model.ProviderConfig
import dev.agentone.core.providers.ProviderType
import java.util.UUID

@Composable
fun OnboardingPage(onComplete: () -> Unit) {
    val app = AgentOneApp.instance
    val security = app.securityManager
    val scope = rememberCoroutineScope()
    val providers = listOf(
        ProviderType.OPENAI to "OpenAI",
        ProviderType.ANTHROPIC to "Anthropic",
        ProviderType.GEMINI to "Gemini",
        ProviderType.DEEPSEEK to "DeepSeek",
        ProviderType.OPENROUTER to "OpenRouter",
        ProviderType.FAKE to "Fake (测试)"
    )

    var selectedProvider by remember { mutableStateOf(providers.first()) }
    var apiKey by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Android,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "欢迎使用 AgentOne",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "你的私人 AI Agent 工作空间。\n请至少配置一个模型提供商以开始使用。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "选择提供商",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        providers.forEach { (type, name) ->
            Button(
                onClick = { selectedProvider = type to name },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                enabled = selectedProvider.first != type
            ) {
                Text(if (selectedProvider.first == type) "> $name" else name)
            }
        }

        if (selectedProvider.first != ProviderType.FAKE) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("${selectedProvider.second} 的 API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val type = selectedProvider.first
                val config = ProviderConfig(
                    id = type.name.lowercase(),
                    type = type.name,
                    displayName = selectedProvider.second,
                    endpoint = when (type) {
                        ProviderType.OPENAI -> "https://api.openai.com"
                        ProviderType.GEMINI -> "https://generativelanguage.googleapis.com"
                        ProviderType.ANTHROPIC -> "https://api.anthropic.com"
                        ProviderType.DEEPSEEK -> "https://api.deepseek.com"
                        ProviderType.OPENROUTER -> "https://openrouter.ai/api"
                        ProviderType.OPENAI_COMPATIBLE -> ""
                        ProviderType.FAKE -> ""
                    },
                    encryptedApiKeyRef = if (type != ProviderType.FAKE) "stored" else null,
                    enabled = true
                )
                scope.launch {
                    app.database.providerConfigDao().upsert(config)
                }
                if (type != ProviderType.FAKE && apiKey.isNotBlank()) {
                    security.saveApiKey(type.name.lowercase(), apiKey)
                }
                security.setOnboardingComplete()
                saved = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存并继续")
        }

        if (saved) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("进入会话")
            }
        }
    }
}
