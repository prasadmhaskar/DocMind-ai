package com.pnm.docmind.service.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.TransientAiException;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatProviderRouterTest {

    private final ChatModel groq = mock(ChatModel.class);
    private final ChatModel gemini = mock(ChatModel.class);
    private final Prompt prompt = new Prompt("Explain the network layer");

    @Test
    void usesGroqWhenPrimarySucceeds() {
        ChatResponse groqResponse = response("Groq answer");
        when(groq.call(prompt)).thenReturn(groqResponse);
        ChatProviderRouter router = new ChatProviderRouter(groq, gemini, "groq");

        assertSame(groqResponse, router.call(prompt));

        verify(gemini, never()).call(prompt);
    }

    @Test
    void fallsBackToGeminiForTransientGroqFailure() {
        ChatResponse geminiResponse = response("Gemini answer");
        when(groq.call(prompt)).thenThrow(new TransientAiException("Groq unavailable"));
        when(gemini.call(prompt)).thenReturn(geminiResponse);
        ChatProviderRouter router = new ChatProviderRouter(groq, gemini, "groq");

        assertSame(geminiResponse, router.call(prompt));
    }

    @Test
    void doesNotFallbackForNonTransientGroqFailure() {
        when(groq.call(prompt)).thenThrow(new IllegalArgumentException("Invalid request"));
        ChatProviderRouter router = new ChatProviderRouter(groq, gemini, "groq");

        assertThrows(IllegalArgumentException.class, () -> router.call(prompt));

        verify(gemini, never()).call(prompt);
    }

    @Test
    void fallsBackBeforeTheFirstStreamDeltaAndNotifiesTheClient() {
        ChatResponse geminiChunk = response("Gemini stream answer");
        when(groq.stream(prompt)).thenReturn(Flux.error(new TransientAiException("Groq unavailable")));
        when(gemini.stream(prompt)).thenReturn(Flux.just(geminiChunk));
        ChatProviderRouter router = new ChatProviderRouter(groq, gemini, "groq");
        AtomicInteger fallbackNotices = new AtomicInteger();

        List<ChatResponse> responses = router.stream(prompt, fallbackNotices::incrementAndGet)
                .collectList()
                .block();

        assertEquals(List.of(geminiChunk), responses);
        assertEquals(1, fallbackNotices.get());
    }

    @Test
    void doesNotSwitchProvidersAfterThePrimaryHasEmittedText() {
        when(groq.stream(prompt)).thenReturn(Flux.concat(
                Flux.just(response("Partial Groq answer")),
                Flux.error(new TransientAiException("Groq stream interrupted"))));
        ChatProviderRouter router = new ChatProviderRouter(groq, gemini, "groq");
        AtomicInteger fallbackNotices = new AtomicInteger();

        assertThrows(RuntimeException.class,
                () -> router.stream(prompt, fallbackNotices::incrementAndGet).blockLast());

        assertEquals(0, fallbackNotices.get());
        verify(gemini, never()).stream(prompt);
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
