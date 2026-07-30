-- modify type of column in table consumed_events from UUID to varchar

ALTER TABLE consumed_events
ALTER COLUMN event_id TYPE VARCHAR(36) USING event_id::VARCHAR(36);