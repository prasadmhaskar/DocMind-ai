package com.pnm.docmind.repository;

import com.pgvector.PGvector;
import com.pnm.docmind.dto.RetrievedChunk;
import com.pnm.docmind.dto.RetrievedChunkResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class DocumentChunkJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<RetrievedChunk> search(
            Long documentId,
            float[] embedding,
            int topK) {

        String sql = """
                SELECT
                    chunk_index,
                    content,
                    1 - (embedding <=> ?) AS similarity
                FROM document_chunks
                WHERE document_id = ?
                ORDER BY embedding <=> ?
                LIMIT ?
                """;

        PGvector queryVector = new PGvector(embedding);

        return jdbcTemplate.query(
                sql,
                ps -> {
                    ps.setObject(1, queryVector);
                    ps.setLong(2, documentId);
                    ps.setObject(3, queryVector);
                    ps.setInt(4, topK);
                },
                (rs, rowNum) -> new RetrievedChunk(
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        rs.getDouble("similarity")
                )
        );
    }
}
