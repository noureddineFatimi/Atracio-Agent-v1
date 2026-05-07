package atracio.agent.dto;

public record ChatRequestDto(
    String tenant,
    String userMessage,
    String conversationId,
    String accessToken,
    String provider
) {}
