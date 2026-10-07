package com.junaldadlawan.event_ticketing_api.post.enums;

/** Where a post is in its life, as of a given moment. Only LIVE posts are public. */
public enum PostStatus {
    /** An admin switched it off for the public. Wins over the dates. */
    HIDDEN,
    /** Its expiry has passed. */
    EXPIRED,
    /** Its publish time has not come yet. */
    SCHEDULED,
    LIVE
}
