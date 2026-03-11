package com.ghouse.socialraven.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
public class UpdatePostCollectionRequest {

    private String title;

    private String description;

    private OffsetDateTime scheduledTime;

    /** Updated platform-specific configuration (replaces existing if provided). */
    private Map<String, Object> platformConfigs;

    /** New media files that have already been uploaded to S3. */
    private List<PostMedia> newMedia;

    /**
     * S3 file keys from the existing media that should be retained.
     * Any existing media whose key is NOT in this list will be removed.
     * If null, existing media is left untouched.
     */
    private List<String> keepMediaKeys;

    /**
     * Updated list of connected accounts for this collection.
     * Posts for removed accounts will be deleted from DB and Redis.
     * Posts for new accounts will be created and added to Redis.
     * Only applied when the collection is still fully SCHEDULED.
     */
    private List<ConnectedAccount> connectedAccounts;
}
