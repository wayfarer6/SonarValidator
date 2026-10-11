-- Convert ISO-8601 UTC strings to PostgreSQL timestamps without losing instants.
-- Run once before starting a version whose Project fields use java.util.Date.
ALTER TABLE project
    ALTER COLUMN created_at TYPE timestamp without time zone
        USING (created_at::timestamptz AT TIME ZONE 'UTC'),
    ALTER COLUMN updated_at TYPE timestamp without time zone
        USING (updated_at::timestamptz AT TIME ZONE 'UTC');