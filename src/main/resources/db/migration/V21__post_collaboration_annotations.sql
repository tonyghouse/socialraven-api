ALTER TABLE socialraven.post_collection_collaboration_thread
    ADD COLUMN media_id BIGINT,
    ADD COLUMN media_marker_x DOUBLE PRECISION,
    ADD COLUMN media_marker_y DOUBLE PRECISION;

ALTER TABLE socialraven.post_collection_collaboration_thread
    ADD CONSTRAINT post_collection_collaboration_media_annotation_check
        CHECK (
            (media_id IS NULL AND media_marker_x IS NULL AND media_marker_y IS NULL)
            OR (
                media_id IS NOT NULL
                AND media_marker_x IS NOT NULL
                AND media_marker_y IS NOT NULL
                AND media_marker_x >= 0
                AND media_marker_x <= 1
                AND media_marker_y >= 0
                AND media_marker_y <= 1
            )
        );
