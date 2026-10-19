package com.junaldadlawan.event_ticketing_api.payout.gateway;

import com.junaldadlawan.event_ticketing_api.common.exception.ApiException;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pays organizers from the platform's PayMongo wallet ({@code payments.payout-gateway=paymongo}): a transfer to a bank account or e-wallet over
 * InstaPay (real time, up to PHP 50,000) or PESONet (next banking day, larger amounts). PayMongo charges the wallet a fee per transfer,
 * which the platform bears. The transfer settles later: the {@code transfer.outward.successful} / {@code transfer.outward.failed}
 * webhook (or {@link #retrieve}) says how it ended. PHP only; amounts are centavos. The account number is sent and never logged.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payments.payout-gateway", havingValue = "paymongo")
public class PayMongoPayoutTransferClient implements PayoutTransferClient {

    /** InstaPay's per-transfer limit in centavos; anything above goes by PESONet. */
    static final long INSTAPAY_LIMIT = 5_000_000L;
    /** PayMongo's BIC for every PayMongo wallet, as the source of a transfer. */
    static final String WALLET_BIC = "PAEYPHM2XXX";

    private final RestClient http;
    private final String v2Base;
    private final String walletNumber;
    private final String walletName;

    public PayMongoPayoutTransferClient(@Qualifier("paymongoRestClient") RestClient http,
                                        @Value("${payments.paymongo.base-url:https://api.paymongo.com/v1}") String baseUrl,
                                        @Value("${payments.paymongo.wallet-account-number:}") String walletNumber,
                                        @Value("${payments.paymongo.wallet-account-name:}") String walletName) {
        this.http = http;
        this.v2Base = baseUrl.replaceAll("/v1/?$", "/v2");
        this.walletNumber = walletNumber;
        this.walletName = walletName;
    }

    @Override
    public boolean requiresAccount() {
        return true;
    }

    @Override
    public List<Bank> banks() {
        JsonNode body = call(() -> http.get().uri("/wallets/receiving_institutions?provider=instapay").retrieve()
                .onStatus(status -> status.isError(), (req, res) -> { }).toEntity(JsonNode.class));
        List<Bank> banks = new ArrayList<>();
        for (JsonNode institution : body.path("data")) {
            JsonNode attributes = institution.path("attributes");
            boolean receiver = false;
            for (JsonNode kind : attributes.path("type")) {
                receiver |= "receiver".equals(kind.asText());
            }
            if (receiver && !attributes.path("provider_code").asText("").isBlank()) {
                banks.add(new Bank(attributes.path("name").asText(), attributes.path("provider_code").asText()));
            }
        }
        return banks;
    }

    @Override
    public WalletInfo wallet() {
        JsonNode list = call(() -> http.get().uri(v2Base + "/wallets/").retrieve()   // the trailing slash matters: without it PayMongo answers 301 with an HTML page
                .onStatus(status -> status.isError(), (req, res) -> { }).toEntity(JsonNode.class));
        JsonNode chosen = null;
        for (JsonNode candidate : list.path("data")) {
            if (chosen == null || candidate.path("is_default").asBoolean(false)) {
                chosen = candidate;
            }
        }
        if (chosen == null) {
            throw new ResourceNotFoundException("This PayMongo account has no wallet yet. Activate one in the PayMongo Dashboard (Wallet), then try again");
        }
        String id = chosen.path("id").asText();
        JsonNode w = call(() -> http.get().uri(v2Base + "/wallets/" + id + "?fields=limits&fields=balance&fields=account").retrieve()
                .onStatus(status -> status.isError(), (req, res) -> { }).toEntity(JsonNode.class)).path("data");
        JsonNode limits = w.path("limits");
        JsonNode running = w.has("running_transaction") ? w.path("running_transaction") : w.path("running_transactions");
        JsonNode account = w.path("account");
        String number = account.path("account_number").asText(null);
        return new WalletInfo("PAYMONGO", w.path("id").asText(id), w.path("status").asText(null), w.path("livemode").asBoolean(false),
                w.path("is_default").asBoolean(false), w.path("type").asText(null), w.path("statement_of_acceptance_signed_at").asText(null),
                w.path("balance").path("available").asLong(), w.path("balance").path("pending").asLong(),
                new WalletLimits(limits.path("balance").asLong(), limits.path("transactions").path("inward").path("daily").asLong(),
                        limits.path("transactions").path("inward").path("monthly").asLong(),
                        limits.path("transactions").path("outward").path("daily").asLong(),
                        limits.path("transactions").path("outward").path("monthly").asLong()),
                new WalletUsage(running.path("inward").path("daily").asLong(), running.path("inward").path("monthly").asLong(),
                        running.path("outward").path("daily").asLong(), running.path("outward").path("monthly").asLong()),
                new WalletAccount(account.path("provider").asText(null), account.path("account_name").asText(null), number,
                        account.path("currency").asText(null)),
                w.path("created_at").asText(null), w.path("updated_at").asText(null),
                walletNumber.isBlank() || number == null ? null : walletNumber.equals(number));
    }

    @Override
    public TransferResult send(TransferRequest request) {
        if (!"PHP".equalsIgnoreCase(request.currency())) {
            throw new BadRequestException("PayMongo only pays out in PHP, not " + request.currency());
        }
        if (walletNumber.isBlank() || walletName.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The PayMongo wallet is not configured (PAYMONGO_WALLET_ACCOUNT_NUMBER and PAYMONGO_WALLET_ACCOUNT_NAME)");
        }
        Map<String, Object> transfer = new LinkedHashMap<>();
        transfer.put("provider", request.amount() > INSTAPAY_LIMIT ? "pesonet" : "instapay");
        transfer.put("amount", request.amount());
        transfer.put("currency", "PHP");
        transfer.put("purpose", "Disbursement");
        transfer.put("description", request.description());
        transfer.put("reference_number", request.reference());
        transfer.put("source_account", Map.of("number", walletNumber, "name", walletName, "bic", WALLET_BIC));
        transfer.put("destination_account", Map.of("number", request.accountNumber(), "name", request.accountName(), "bic", request.bankCode()));
        transfer.put("metadata", request.metadata());

        ResponseEntity<JsonNode> response = exchange(() -> http.post().uri(v2Base + "/batch_transfers")
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("transfers", List.of(transfer))).retrieve()
                .onStatus(status -> status.isError(), (req, res) -> { }).toEntity(JsonNode.class));
        if (response.getStatusCode().is5xxServerError() || response.getBody() == null) {
            throw unavailable();
        }
        if (response.getStatusCode().isError()) {
            // PayMongo said no before sending anything: safe to retry once the reason is fixed
            return new TransferResult(null, Status.FAILED, errorDetail(response.getBody(), "PayMongo rejected the transfer"));
        }
        JsonNode created = response.getBody().path("data").path("transfers").path(0);
        if (created.path("id").asText("").isBlank()) {
            throw unavailable();
        }
        return result(created, created.path("id").asText());
    }

    @Override
    public TransferResult retrieve(String transferId) {
        ResponseEntity<JsonNode> response = exchange(() -> http.get().uri(v2Base + "/transfers/" + transferId).retrieve()
                .onStatus(status -> status.isError(), (req, res) -> { }).toEntity(JsonNode.class));
        if (response.getStatusCode().isError() || response.getBody() == null) {
            throw unavailable();
        }
        JsonNode data = response.getBody().path("data");
        return result(data.has("attributes") ? data.path("attributes") : data, data.path("id").asText(transferId));
    }

    /** One transfer object ({@code id}, {@code status}, and on failure the provider's error) to a result. */
    private static TransferResult result(JsonNode transfer, String id) {
        Status status = switch (transfer.path("status").asText("")) {
            case "succeeded" -> Status.SUCCEEDED;
            case "failed" -> Status.FAILED;
            default -> Status.PENDING;
        };
        String reason = null;
        if (status == Status.FAILED) {
            reason = firstText(transfer, "provider_error", "failure_message", "error_message");
            String code = firstText(transfer, "provider_error_code", "failure_code", "error_code");
            reason = reason == null ? (code == null ? "The transfer failed" : code) : (code == null ? reason : code + ": " + reason);
        }
        return new TransferResult(id, status, reason);
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private JsonNode call(java.util.function.Supplier<ResponseEntity<JsonNode>> request) {
        ResponseEntity<JsonNode> response = exchange(request);
        if (response.getStatusCode().isError() || response.getBody() == null) {
            throw unavailable();
        }
        return response.getBody();
    }

    private ResponseEntity<JsonNode> exchange(java.util.function.Supplier<ResponseEntity<JsonNode>> request) {
        try {
            return request.get();
        } catch (ResourceAccessException e) {
            log.warn("PayMongo transfer call failed: could not reach the provider");
            throw unavailable();
        } catch (RestClientException e) {
            // an answer that is not the JSON expected (a redirect or error page): the outcome is unknown, never a 500
            log.warn("PayMongo transfer call failed: unreadable response ({})", e.getClass().getSimpleName());
            throw unavailable();
        }
    }

    private static String errorDetail(JsonNode body, String fallback) {
        String detail = body.path("errors").path(0).path("detail").asText("");
        return detail.isBlank() ? fallback : detail;
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "PayMongo is unavailable, so the transfer could not be confirmed");
    }
}
