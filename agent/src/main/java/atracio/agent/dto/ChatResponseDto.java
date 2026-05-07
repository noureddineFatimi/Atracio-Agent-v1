package atracio.agent.dto;

import java.util.List;

public record ChatResponseDto(
    String conversationId,
    String assistantMessage,
    List<ToolCallDto> toolCalls 
) {}
