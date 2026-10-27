package com.junaldadlawan.event_ticketing_api.region;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.region.gateway.ExchangeRateClient;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Platform currencies and countries through the real security chain, with the exchange-rate feed mocked. The two small tables are
 * snapshotted before each test and put back afterwards, so whatever the admin set up stays as it was.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegionIntegrationTest {

    // codes no seed uses, so the "not enabled" cases are not enabled
    private static final String CODE = "SGD";
    private static final String COUNTRY = "SG";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;
    @MockitoBean
    private ExchangeRateClient exchangeRateClient;

    private final User admin = User.builder().id(UUID.randomUUID()).email("region-admin-" + UUID.randomUUID() + "@test.local").role(Role.ADMIN).build();
    private final User applicant = User.builder().id(UUID.randomUUID()).email("region-app-" + UUID.randomUUID() + "@test.local").role(Role.CUSTOMER).build();
    private final List<UUID> orgIds = new ArrayList<>();
    private List<Map<String, Object>> currencies;
    private List<Map<String, Object>> countries;

    @BeforeEach
    void snapshot() {
        currencies = jdbc.queryForList("select * from supported_currencies");
        countries = jdbc.queryForList("select * from supported_countries");
        // start from SGD / SG not enabled, whatever the database had
        jdbc.update("delete from supported_countries where code = ?", COUNTRY);
        jdbc.update("delete from supported_currencies where code = ?", CODE);
    }

    @AfterEach
    void restore() {
        for (UUID orgId : orgIds) {
            organizationMemberRepository.deleteAll(organizationMemberRepository.findByOrganizationId(orgId));
            organizationRepository.deleteById(orgId);
        }
        jdbc.update("delete from audit_log_entries where actor_id in (?, ?)", admin.getId(), applicant.getId());
        jdbc.update("delete from supported_countries");
        jdbc.update("delete from supported_currencies");
        SimpleJdbcInsert currencyInsert = new SimpleJdbcInsert(jdbc).withTableName("supported_currencies");
        currencies.forEach(currencyInsert::execute);
        SimpleJdbcInsert countryInsert = new SimpleJdbcInsert(jdbc).withTableName("supported_countries");
        countries.forEach(countryInsert::execute);
    }

    private String token(User user) {
        return "Bearer " + jwtService.generateAccessToken(user);
    }

    private ResultActions enable(String code) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/currencies").header("Authorization", token(admin))
                .contentType("application/json").content("{\"code\":\"" + code + "\"}"));
    }

    private ResultActions putCountry(String code, String currency) throws Exception {
        return mockMvc.perform(put("/api/v1/admin/countries/{code}", code).header("Authorization", token(admin))
                .contentType("application/json").content("{\"currencyCode\":\"" + currency + "\"}"));
    }

    private JsonNode regions() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/platform/regions"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "max-age=300, public"))
                .andReturn().getResponse().getContentAsString());
    }

    private static boolean has(JsonNode list, String code) {
        for (JsonNode node : list) {
            if (code.equals(node.get("code").asText())) {
                return true;
            }
        }
        return false;
    }

    private long audits(String action) {
        return jdbc.queryForObject("select count(*) from audit_log_entries where action = ? and actor_id = ?", Long.class, action, admin.getId());
    }

    @Test
    void enablingACurrencyFetchesItsRate_andTheRegionsShowIt() throws Exception {
        when(exchangeRateClient.usdRates()).thenReturn(Map.of(CODE, new BigDecimal("1.2812")));

        enable("sgd").andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(CODE))
                .andExpect(jsonPath("$.name").value("Singapore Dollar"))
                .andExpect(jsonPath("$.minorUnits").value(2))
                .andExpect(jsonPath("$.usdRate").value(1.2812))
                .andExpect(jsonPath("$.rateStale").value(false));
        enable(CODE).andExpect(status().isConflict());
        putCountry(COUNTRY, CODE).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Singapore"));

        JsonNode regions = regions();
        assertThat(regions.get("baseCurrency").asText()).isEqualTo("USD");
        assertThat(has(regions.get("currencies"), CODE)).isTrue();
        assertThat(has(regions.get("currencies"), "USD")).isTrue();
        assertThat(has(regions.get("countries"), COUNTRY)).isTrue();
        assertThat(has(regions.get("countries"), "PH")).isTrue();
        assertThat(audits("currency.enabled")).isEqualTo(1);
        assertThat(audits("country.enabled")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select details::text from audit_log_entries where action = 'currency.enabled' and actor_id = ?",
                String.class, admin.getId())).contains("frankfurter").contains("1.2812");
    }

    @Test
    void aCurrencyTheFeedHasNoRateFor_isNotEnabled() throws Exception {
        when(exchangeRateClient.usdRates()).thenReturn(Map.of("EUR", new BigDecimal("0.9")));
        enable(CODE).andExpect(status().isConflict());

        when(exchangeRateClient.usdRates()).thenThrow(new IllegalStateException("feed down"));
        enable(CODE).andExpect(status().isBadGateway());

        enable("ZZZ").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from supported_currencies where code = ?", Long.class, CODE)).isZero();
        assertThat(has(regions().get("currencies"), CODE)).isFalse();
    }

    @Test
    void aFailedRefreshKeepsTheLastRateAndMarksItStale() throws Exception {
        when(exchangeRateClient.usdRates()).thenReturn(Map.of(CODE, new BigDecimal("1.28")));
        enable(CODE).andExpect(status().isCreated());

        when(exchangeRateClient.usdRates()).thenThrow(new IllegalStateException("feed down"));
        mockMvc.perform(post("/api/v1/admin/currencies/refresh").header("Authorization", token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='SGD')].rateStale").value(true))
                .andExpect(jsonPath("$[?(@.code=='SGD')].usdRate").value(1.28))
                .andExpect(jsonPath("$[?(@.code=='USD')].rateStale").value(false));

        org.mockito.Mockito.reset(exchangeRateClient);
        when(exchangeRateClient.usdRates()).thenReturn(Map.of(CODE, new BigDecimal("1.30")));
        mockMvc.perform(post("/api/v1/admin/currencies/refresh").header("Authorization", token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='SGD')].rateStale").value(false))
                .andExpect(jsonPath("$[?(@.code=='SGD')].usdRate").value(1.30));
        assertThat(audits("currency.rate_refreshed")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void aCurrencyOrCountryInUseCannotBeDisabled() throws Exception {
        when(exchangeRateClient.usdRates()).thenReturn(Map.of(CODE, new BigDecimal("1.28")));
        mockMvc.perform(delete("/api/v1/admin/currencies/USD").header("Authorization", token(admin))).andExpect(status().isConflict());
        putCountry(COUNTRY, CODE).andExpect(status().isBadRequest());   // the currency is not enabled yet
        enable(CODE).andExpect(status().isCreated());
        putCountry(COUNTRY, CODE).andExpect(status().isOk());

        // the country uses the currency
        mockMvc.perform(delete("/api/v1/admin/currencies/{code}", CODE).header("Authorization", token(admin))).andExpect(status().isConflict());

        // an organization is in the country, with its currency
        Organization org = organizationRepository.save(Organization.builder().name("Region Test Org " + UUID.randomUUID())
                .status(OrganizationStatus.APPROVED).country(COUNTRY).currency(CODE).documents(List.of()).build());
        orgIds.add(org.getId());
        mockMvc.perform(delete("/api/v1/admin/countries/{code}", COUNTRY).header("Authorization", token(admin))).andExpect(status().isConflict());

        // moved elsewhere: the country can go, the currency is still the organization's
        org.setCountry("PH");
        organizationRepository.save(org);
        mockMvc.perform(delete("/api/v1/admin/countries/{code}", COUNTRY).header("Authorization", token(admin))).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/admin/currencies/{code}", CODE).header("Authorization", token(admin))).andExpect(status().isConflict());

        organizationRepository.deleteById(org.getId());
        orgIds.remove(org.getId());
        mockMvc.perform(delete("/api/v1/admin/currencies/{code}", CODE).header("Authorization", token(admin))).andExpect(status().isNoContent());
        assertThat(audits("currency.disabled")).isEqualTo(1);
        assertThat(audits("country.disabled")).isEqualTo(1);
        assertThat(has(regions().get("currencies"), CODE)).isFalse();
    }

    @Test
    void onlyAnAdminManagesRegions() throws Exception {
        mockMvc.perform(post("/api/v1/admin/currencies").header("Authorization", token(applicant))
                .contentType("application/json").content("{\"code\":\"SGD\"}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/countries/SG").header("Authorization", token(applicant))
                .contentType("application/json").content("{\"currencyCode\":\"PHP\"}")).andExpect(status().isForbidden());
    }

    @Test
    void anOrganizationMustBeInAnEnabledCountry_andGetsItsCurrencyWhenApproved() throws Exception {
        String docs = "\"documents\":[{\"type\":\"business_permit\",\"url\":\"https://docs/permit.pdf\"}]";
        mockMvc.perform(post("/api/v1/organizations").header("Authorization", token(applicant)).contentType("application/json")
                .content("{\"name\":\"Region Org " + UUID.randomUUID() + "\",\"country\":\"SG\"," + docs + "}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/organizations").header("Authorization", token(applicant)).contentType("application/json")
                .content("{\"name\":\"Region Org " + UUID.randomUUID() + "\"," + docs + "}")).andExpect(status().isBadRequest());

        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/v1/organizations").header("Authorization", token(applicant))
                        .contentType("application/json").content("{\"name\":\"Region Org " + UUID.randomUUID() + "\",\"country\":\"ph\"," + docs + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.country").value("PH")).andExpect(jsonPath("$.currency").isEmpty())
                .andReturn().getResponse().getContentAsString());
        UUID id = UUID.fromString(created.get("id").asText());
        orgIds.add(id);

        mockMvc.perform(post("/api/v1/organizations/{id}/approve", id).header("Authorization", token(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currency").value("PHP"));
    }
}
