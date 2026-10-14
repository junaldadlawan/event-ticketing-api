package com.junaldadlawan.event_ticketing_api.common.validation;

/** The characters a person's name and email may contain, shared by registration and the profile updates. */
public final class UserInput {

    private UserInput() {
    }

    /** Starts with a letter (any language), then letters, accents, spaces, '.', apostrophes and '-'. */
    public static final String NAME_REGEX = "\\p{L}[\\p{L}\\p{M} .'\\u2019-]*";

    /** A middle name is optional: the same rule, or empty. */
    public static final String MIDDLE_NAME_REGEX = "(" + NAME_REGEX + ")?";

    /**
     * Plain ASCII email: the part before the @ starts and ends with a letter or digit and may use . _ % + - between
     * them (never doubled); the domain is dot-separated labels ending in a TLD of 2+ letters.
     */
    public static final String EMAIL_REGEX =
            "[A-Za-z0-9]+([._%+-][A-Za-z0-9]+)*@([A-Za-z0-9]+(-[A-Za-z0-9]+)*\\.)+[A-Za-z]{2,}";

    /** International format: + then 8-15 digits, first digit not 0 (E.164). Nothing else: no spaces, dashes or brackets. */
    public static final String PHONE_REGEX = "\\+[1-9][0-9]{7,14}";

    /** Optional phone number: the same rule, or empty. */
    public static final String PHONE_OR_EMPTY_REGEX = "(" + PHONE_REGEX + ")?";

    public static final String PHONE_MESSAGE = "must be an international phone number: + followed by 8 to 15 digits, nothing else";

    public static final String NAME_MESSAGE = "may only contain letters, spaces, '.', apostrophes and '-'";
    public static final String EMAIL_MESSAGE = "must be a valid email address (letters, digits and . _ % + - only)";
}
