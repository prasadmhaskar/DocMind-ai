# Gemini embedding model test rollback

Before the model change, copies of the application configuration and embedding service were saved here:

- `/tmp/docmind-application-before-embedding-task-test.yml`
- `/tmp/docmind-EmbeddingServiceImpl-before-task-test.java`

The `V11` migration preserves the existing chunk rows and document statuses in backup tables. To revert after the new model has been used:

1. Stop the application.
2. Restore the two source files above to `src/main/resources/application.yml` and `src/main/java/com/pnm/docmind/service/embedding/EmbeddingServiceImpl.java`.
3. Run the database rollback script against the same database configured for the app. For PostgreSQL, from the project root:

   ```bash
   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/rollback/rollback_gemini_embedding_001.sql
   ```

   If `DATABASE_URL` is not set, connect with `psql -h HOST -U USER -d DATABASE -v ON_ERROR_STOP=1 -f src/main/resources/db/rollback/rollback_gemini_embedding_001.sql`.
4. Restart the application. Documents uploaded during the model test are queued for re-embedding using the restored model.

The script restores the saved chunk vectors and document statuses, then queues documents created during the model test for re-embedding. It runs in a transaction and does not delete the backup tables. Keep those tables until the rollback is confirmed; document deletion cascades to that document's backup rows. The script uses the `fail_on_rebuild_failure` column introduced by `V11`, which remains part of the current application schema.
