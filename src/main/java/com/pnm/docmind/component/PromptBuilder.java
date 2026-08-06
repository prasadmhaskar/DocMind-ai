package com.pnm.docmind.component;

import com.pnm.docmind.dto.RetrievedChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@Slf4j
public class PromptBuilder {

    public String build(String question, List<RetrievedChunk> chunks){

        log.info("Building prompt using {} retrieved chunks", chunks.size());

        StringBuilder sb = new StringBuilder();

        String baseFormat = """
               
                You are an AI assistant specialized in answering questions from uploaded documents.
                
                Rules:
                
                1. Answer ONLY using the supplied context.
                
                2. Never use outside knowledge.
                
                3. If the answer is not present in the context, reply exactly:
                
                "I couldn't find the answer in the document."
                
                4. Keep the answer concise.
                
                Context:
                
                ...
                """;

        sb.append(baseFormat);

        int index = 1;

        for(RetrievedChunk chunk : chunks){
            sb.append("""
            
            ---- Context %d ----
            """.formatted(index++));

            sb.append(chunk.content());

            sb.append("\n");
        }

        sb.append("Question:\n");
        sb.append(question);

        sb.append("\n\nAnswer:");

        return  sb.toString();

    }
}
