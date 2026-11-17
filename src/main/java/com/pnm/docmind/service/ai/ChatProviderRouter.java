package com.pnm.docmind.service.ai;

import com.google.genai.errors.ApiException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.OpenAIRetryableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Primary
@Slf4j
public class ChatProviderRouter implements ChatModel {

    private final ChatModel groq;
    private final ChatModel gemini;
    private final String primaryProvider;

    public ChatProviderRouter(
            @Qualifier("openAiChatModel") ChatModel groq,
            @Qualifier("geminiChatModel") ChatModel gemini,
            @Value("${app.ai.chat.primary:groq}") String primaryProvider) {
        this.groq = groq;
        this.gemini = gemini;
        this.primaryProvider = primaryProvider.trim().toLowerCase(Locale.ROOT);
        if (!this.primaryProvider.equals("groq") && !this.primaryProvider.equals("gemini")) {
            throw new IllegalArgumentException("app.ai.chat.primary must be 'groq' or 'gemini'");
        }
        log.info("Chat provider fallback configured: primary={}, fallback={}", primaryName(), fallbackName());
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        ChatModel primary = primaryModel();
        ChatModel fallback = fallbackModel();
        String primaryName = primaryName();
        String fallbackName = fallbackName();

        try {
            return primary.call(prompt);
        } catch (RuntimeException primaryFailure) {
            if (!isTransientFailure(primaryFailure)) {
                throw primaryFailure;
            }

            log.warn("Primary chat provider {} failed transiently; retrying with {} ({})",
                    primaryName, fallbackName, primaryFailure.getClass().getSimpleName());
            try {
                return fallback.call(prompt);
            } catch (RuntimeException fallbackFailure) {
                fallbackFailure.addSuppressed(primaryFailure);
                throw fallbackFailure;
            }
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return stream(prompt, () -> { });
    }

    public Flux<ChatResponse> stream(Prompt prompt, Runnable onFallback) {
        return Flux.defer(() -> {
            AtomicBoolean emittedText = new AtomicBoolean(false);
            return Flux.defer(() -> primaryModel().stream(prompt))
                    .doOnNext(response -> {
                        if (containsText(response)) {
                            emittedText.set(true);
                        }
                    })
                    .onErrorResume(primaryFailure -> {
                        if (emittedText.get() || !isTransientFailure(primaryFailure)) {
                            return Flux.error(primaryFailure);
                        }

                        log.warn("Primary streaming provider {} failed before output; retrying with {} ({})",
                                primaryName(), fallbackName(), primaryFailure.getClass().getSimpleName());
                        onFallback.run();
                        return Flux.defer(() -> fallbackModel().stream(prompt))
                                .doOnError(fallbackFailure -> fallbackFailure.addSuppressed(primaryFailure));
                    });
        });
    }

    public Flux<String> streamText(String prompt, Runnable onFallback) {
        return stream(new Prompt(prompt), onFallback)
                .map(response -> {
                    Generation result = response == null ? null : response.getResult();
                    return result == null || result.getOutput() == null
                            ? ""
                            : result.getOutput().getText();
                });
    }

    private boolean containsText(ChatResponse response) {
        Generation result = response == null ? null : response.getResult();
        return result != null
                && result.getOutput() != null
                && result.getOutput().getText() != null
                && !result.getOutput().getText().isEmpty();
    }

    private boolean isTransientFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof TransientAiException
                    || current instanceof OpenAIRetryableException
                    || current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException
                    || current instanceof SocketException
                    || current instanceof IOException
                    || current instanceof TimeoutException) {
                return true;
            }
            if (current instanceof ApiException googleFailure) {
                int status = googleFailure.code();
                if (status == 408 || status == 429 || status >= 500) {
                    return true;
                }
            }
            if (current instanceof OpenAIServiceException groqFailure) {
                int status = groqFailure.statusCode();
                if (status == 408 || status == 429 || status >= 500) {
                    return true;
                }
            }
        }
        return false;
    }

    private ChatModel primaryModel() {
        return primaryProvider.equals("groq") ? groq : gemini;
    }

    private ChatModel fallbackModel() {
        return primaryProvider.equals("groq") ? gemini : groq;
    }

    private String primaryName() {
        return primaryProvider.equals("groq") ? "Groq" : "Gemini";
    }

    private String fallbackName() {
        return primaryProvider.equals("groq") ? "Gemini" : "Groq";
    }
}
