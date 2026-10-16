package com.junaldadlawan.event_ticketing_api.common;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * For integration tests that assert exact cart and order totals: the dev database holds the admin's real platform fee rules and payment
 * gateway fee, which would change those totals. Before each test it sets every live platform fee rule aside and switches the gateway fee off;
 * after it, it puts back exactly what it set aside. Add with {@code @ExtendWith(NoFeesExtension.class)} on a {@code @SpringBootTest} class.
 */
public class NoFeesExtension implements BeforeEachCallback, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(NoFeesExtension.class);

    private record Saved(List<UUID> setAsideRules, Map<String, Object> gatewayFee) {
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        JdbcTemplate jdbc = jdbc(context);
        List<UUID> rules = jdbc.queryForList("select id from platform_fee_rules where deleted_at is null", UUID.class);
        Map<String, Object> gateway = jdbc.queryForMap("select percentage, flat_amount, flat_currency, payer, foreign_percentage, foreign_flat_amount from payment_gateway_fee_settings where id = 1");
        context.getStore(NAMESPACE).put("saved", new Saved(rules, gateway));
        rules.forEach(id -> jdbc.update("update platform_fee_rules set deleted_at = now() where id = ?", id));
        jdbc.update("update payment_gateway_fee_settings set percentage = 0, flat_amount = 0, payer = 'PLATFORM', foreign_percentage = null, foreign_flat_amount = null where id = 1");
    }

    @Override
    public void afterEach(ExtensionContext context) {
        Saved saved = context.getStore(NAMESPACE).remove("saved", Saved.class);
        if (saved == null) {
            return;
        }
        JdbcTemplate jdbc = jdbc(context);
        saved.setAsideRules().forEach(id -> jdbc.update("update platform_fee_rules set deleted_at = null where id = ?", id));
        Map<String, Object> g = saved.gatewayFee();
        jdbc.update("update payment_gateway_fee_settings set percentage = ?, flat_amount = ?, flat_currency = ?, payer = ?, foreign_percentage = ?, foreign_flat_amount = ? where id = 1",
                g.get("percentage"), g.get("flat_amount"), g.get("flat_currency"), g.get("payer"), g.get("foreign_percentage"), g.get("foreign_flat_amount"));
    }

    private static JdbcTemplate jdbc(ExtensionContext context) {
        return SpringExtension.getApplicationContext(context).getBean(JdbcTemplate.class);
    }
}
