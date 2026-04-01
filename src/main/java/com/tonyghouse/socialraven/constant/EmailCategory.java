package com.tonyghouse.socialraven.constant;

/**
 * INTERNAL — admin/ops emails (e.g. escalations). Zoho is primary; Resend is last-resort fallback.
 * EXTERNAL — user-facing emails (e.g. invitations, notifications). Resend is primary; Zoho is fallback.
 */
public enum EmailCategory {
    INTERNAL,
    EXTERNAL
}
