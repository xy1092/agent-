package dev.agentone.core.providers

class ProviderRegistry {
    private val providers = mutableMapOf<ProviderType, Provider>()

    init {
        register(ProviderType.OPENAI, OpenAICompatibleProvider(ProviderType.OPENAI, "https://api.openai.com"))
        register(ProviderType.ANTHROPIC, AnthropicProvider())
        register(ProviderType.GEMINI, GeminiProvider())
        register(ProviderType.OPENROUTER, OpenAICompatibleProvider(ProviderType.OPENROUTER, "https://openrouter.ai/api"))
        register(ProviderType.OPENAI_COMPATIBLE, OpenAICompatibleProvider(ProviderType.OPENAI_COMPATIBLE, ""))
        register(ProviderType.FAKE, FakeProvider())
    }

    fun register(type: ProviderType, provider: Provider) {
        providers[type] = provider
    }

    fun get(type: ProviderType): Provider = providers[type]
        ?: throw IllegalArgumentException("No provider registered for $type")

    fun getByTypeString(type: String): Provider {
        val providerType = ProviderType.valueOf(type.uppercase())
        return get(providerType)
    }

    fun allTypes(): List<ProviderType> = ProviderType.entries.toList()
    fun all(): Map<ProviderType, Provider> = providers.toMap()
}
