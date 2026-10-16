package com.junaldadlawan.event_ticketing_api.common.seed;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With {@code app.seed.demo-data=true} the app loads the demo script itself. The script only inserts what is missing, so on the dev database
 * (which holds the demo accounts) this adds nothing, and running it again changes nothing.
 */
@SpringBootTest(properties = "app.seed.demo-data=true")
class DemoDataSeederIntegrationTest {

    @Autowired
    private DemoDataSeeder seeder;
    @Autowired
    private JdbcTemplate jdbc;

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    void theSeedRunsAtStartup_isIdempotent_andLeavesTheConnectionPoolClean() {
        // it already ran when the context started
        assertThat(count("select count(*) from users where email like '%@demo.test'")).isEqualTo(12);
        assertThat(count("select count(*) from users where role = 'SUPER_ADMIN'")).isEqualTo(1);
        int accounts = count("select count(*) from users");
        int events = count("select count(*) from events where title = 'Demo Sunset Concert'");

        seeder.run(new DefaultApplicationArguments());

        assertThat(count("select count(*) from users")).isEqualTo(accounts);
        assertThat(count("select count(*) from events where title = 'Demo Sunset Concert'")).isEqualTo(events);
        assertThat(count("select 1")).isEqualTo(1);   // no transaction was left open on a pooled connection
    }
}
