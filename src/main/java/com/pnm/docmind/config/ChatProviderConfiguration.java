package com.pnm.docmind.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import java.time.Duration;

@Configuration
public class ChatProviderConfiguration {

    @Bean("geminiChatModel")
    GoogleGenAiChatModel geminiChatModel(
            @org.springframework.beans.factory.annotation.Value("${spring.ai.google.genai.embedding.api-key}")
            String apiKey,
            @org.springframework.beans.factory.annotation.Value("${app.ai.chat.gemini.model:gemini-3.6-flash}")
            String model,
            @org.springframework.beans.factory.annotation.Value("${app.ai.chat.gemini.timeout:30s}")
            Duration timeout) {

        Client client = Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder()
                        .timeout(Math.toIntExact(timeout.toMillis()))
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                        .build())
                .build();

        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .model(model)
                .temperature(0.2)
                .thinkingLevel(GoogleGenAiThinkingLevel.MINIMAL)
                .build();

        return GoogleGenAiChatModel.builder()
                .genAiClient(client)
                .options(options)
                .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0)))
                .build();
    }
}
