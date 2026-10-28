package com.junaldadlawan.event_ticketing_api.common;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.ThreadLocalRandom;

/** Ticket prefixes for events that tests insert directly: the column is unique and the database is shared with other tests and demo data. */
public final class TestTicketPrefixes {

    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private TestTicketPrefixes() {
    }

    /** A 3-letter prefix no event has yet. */
    public static String free(JdbcTemplate jdbc) {
        while (true) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            String candidate = "" + LETTERS.charAt(random.nextInt(26)) + LETTERS.charAt(random.nextInt(26)) + LETTERS.charAt(random.nextInt(26));
            if (jdbc.queryForObject("select count(*) from events where ticket_prefix = ?", Integer.class, candidate) == 0) {
                return candidate;
            }
        }
    }
}
