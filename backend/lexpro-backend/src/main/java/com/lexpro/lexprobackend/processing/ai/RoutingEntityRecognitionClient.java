package com.lexpro.lexprobackend.processing.ai;

import com.lexpro.lexprobackend.processing.config.AiServiceProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class RoutingEntityRecognitionClient implements EntityRecognitionClient {

    private final AiServiceProperties properties;
    private final InternalAiServiceEntityRecognitionClient internalClient;
    private final OpenAiCompatibleEntityRecognitionClient legacyClient;

    public RoutingEntityRecognitionClient(AiServiceProperties properties,
                                          InternalAiServiceEntityRecognitionClient internalClient,
                                          OpenAiCompatibleEntityRecognitionClient legacyClient) {
        this.properties = properties;
        this.internalClient = internalClient;
        this.legacyClient = legacyClient;
    }

    @Override
    public EntityRecognitionOutput recognize(String text, String requestId) {
        return properties.isEnabled()
                ? internalClient.recognize(text, null, requestId)
                : legacyClient.recognize(text, requestId);
    }

    @Override
    public EntityRecognitionOutput recognize(String text, String parsedTextJson, String requestId) {
        return properties.isEnabled()
                ? internalClient.recognize(text, parsedTextJson, requestId)
                : legacyClient.recognize(text, requestId);
    }
}
