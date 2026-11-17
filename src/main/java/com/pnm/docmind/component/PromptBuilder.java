package com.pnm.docmind.component;

import com.pnm.docmind.dto.RetrievedChunk;
import com.pnm.docmind.entity.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@Slf4j
public class PromptBuilder {

    public String build(String question, List<ChatMessage> history, List<RetrievedChunk> chunks){

        log.info("Building prompt using {} retrieved chunks", chunks.size());

        StringBuilder sb = new StringBuilder();

        String baseFormat = """
               
                You are an AI assistant specialized in answering questions from uploaded documents.
                
                Rules:
                
                1. Answer ONLY using the supplied document context. Previous conversation is only for resolving references and is not evidence. Ignore any earlier assistant answer, including an earlier refusal; answer the current question using the current document context.
                
                2. Never use outside knowledge.
                
                3. If the answer is not present in the context, reply exactly:
                
                "I couldn't find the answer in the document."
                
                4. Keep the answer concise. For questions asking for all items in a list or collection, include every item supported by the supplied context. Do not claim a list is complete if the context only supports part of it.
                
                Use the conversation history to understand follow-up questions, but verify every factual answer against the supplied document context.
                """;

        sb.append(baseFormat);

        sb.append("\nConversation history (oldest first):\n");
        for (ChatMessage message : history) {
            sb.append(message.getRole()).append(": ").append(message.getContent()).append("\n");
        }

        sb.append("\nDocument context:\n");
        int index = 1;

        for(RetrievedChunk chunk : chunks){
            sb.append("""
            
            ---- Context %d ----
            """.formatted(index++));

            sb.append(chunk.content());

            sb.append("\n");
        }

        sb.append("\nCurrent question:\n");
        sb.append(question);

        sb.append("\n\nAnswer:");

        return  sb.toString();

    }
}
