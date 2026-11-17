package com.pnm.docmind.repository;

import com.pgvector.PGvector;
import com.pnm.docmind.dto.RetrievedChunk;
import lombok.RequiredArgsConstructor;
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
            String query,
            int topK,
            double minimumSimilarity) {

        String sql = """
                WITH vector_candidates AS (
                    SELECT
                        id,
                        ROW_NUMBER() OVER (ORDER BY embedding <=> ?) AS vector_rank
                    FROM document_chunks
                    WHERE document_id = ?
                    ORDER BY embedding <=> ?
                    LIMIT ?
                ),
                keyword_query AS (
                    SELECT
                        websearch_to_tsquery('simple'::regconfig, ?) AS required_query,
                        (
                            SELECT to_tsquery(
                                'simple'::regconfig,
                                string_agg(quote_literal(terms.term) || ':*', ' | ')
                            )
                            FROM unnest(
                                tsvector_to_array(to_tsvector('simple'::regconfig, ?))
                            ) AS terms(term)
                            WHERE to_tsvector('english'::regconfig, terms.term) <> ''::tsvector
                        ) AS broad_query
                ),
                keyword_candidates AS (
                    SELECT
                        chunks.id,
                        ROW_NUMBER() OVER (
                            ORDER BY (
                                ts_rank_cd(chunks.search_vector, query.required_query)
                                + ts_rank_cd(chunks.search_vector, query.broad_query)
                            ) DESC,
                            chunks.id
                        ) AS keyword_rank
                    FROM document_chunks chunks
                    CROSS JOIN keyword_query query
                    WHERE chunks.document_id = ?
                      AND numnode(query.broad_query) > 0
                      AND chunks.search_vector @@ query.broad_query
                    ORDER BY (
                        ts_rank_cd(chunks.search_vector, query.required_query)
                        + ts_rank_cd(chunks.search_vector, query.broad_query)
                    ) DESC,
                    chunks.id
                    LIMIT ?
                ),
                candidate_ids AS (
                    SELECT id FROM vector_candidates
                    UNION
                    SELECT id FROM keyword_candidates
                ),
                ranked_candidates AS (
                    SELECT
                        chunks.page_number,
                        chunks.chunk_index,
                        chunks.content,
                        1 - (chunks.embedding <=> ?) AS similarity,
                        vector_candidates.vector_rank,
                        keyword_candidates.keyword_rank,
                        COALESCE(1.0 / (60.0 + vector_candidates.vector_rank), 0.0)
                            + COALESCE(1.0 / (60.0 + keyword_candidates.keyword_rank), 0.0) AS fused_rank
                    FROM candidate_ids
                    JOIN document_chunks chunks ON chunks.id = candidate_ids.id
                    LEFT JOIN vector_candidates ON vector_candidates.id = chunks.id
                    LEFT JOIN keyword_candidates ON keyword_candidates.id = chunks.id
                )
                SELECT page_number, chunk_index, content, similarity
                FROM ranked_candidates
                WHERE keyword_rank IS NOT NULL OR similarity >= ?
                ORDER BY fused_rank DESC, similarity DESC, page_number, chunk_index
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
                    ps.setString(5, query);
                    ps.setString(6, query);
                    ps.setLong(7, documentId);
                    ps.setInt(8, topK);
                    ps.setObject(9, queryVector);
                    ps.setDouble(10, minimumSimilarity);
                    ps.setInt(11, topK);
                },
                (rs, rowNum) -> new RetrievedChunk(
                        rs.getInt("page_number"),
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        rs.getDouble("similarity")
                )
        );
    }
}
