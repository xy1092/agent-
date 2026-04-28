package dev.agentone.core.agent

import dev.agentone.core.providers.ToolCall
import dev.agentone.core.providers.ToolResult

sealed interface AgentEvent {
    data class RunStarted(val runId: String) : AgentEvent
    data class AssistantTextDelta(val text: String) : AgentEvent
    data class ToolCallRequested(val toolCalls: List<ToolCall>) : AgentEvent
    data class ToolApprovalRequired(val toolCalls: List<ToolCall>) : AgentEvent
    data class ToolApproved(val toolCallId: String) : AgentEvent
    data class ToolRejected(val toolCallId: String) : AgentEvent
    data class ToolExecutionStarted(val toolCallId: String, val toolName: String) : AgentEvent
    data class ToolExecutionCompleted(val toolResult: ToolResult) : AgentEvent
    data class StepLogged(val index: Int, val summary: String) : AgentEvent
    data class RunCompleted(val runId: String, val totalSteps: Int) : AgentEvent
    data class RunFailed(val runId: String, val error: String) : AgentEvent
    data class RunCancelled(val runId: String) : AgentEvent
}
