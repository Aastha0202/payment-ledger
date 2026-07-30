-- modify type of column in table consumed_events from UUID to varchar

ALTER TABLE consumed_events
ALTER COLUMN event_id TYPE UUID USING event_id::UUID;