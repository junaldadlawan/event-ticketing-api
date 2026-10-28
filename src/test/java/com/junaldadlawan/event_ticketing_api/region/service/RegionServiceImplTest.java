package com.junaldadlawan.event_ticketing_api.region.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Ticket type prices must be in the organization's currency (the check TicketTypeServiceImpl calls). */
@ExtendWith(MockitoExtension.class)
class RegionServiceImplTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private RegionServiceImpl regionService;

    private final UUID orgId = UUID.randomUUID();

    private void organizationWithCurrency(String currency) {
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(Organization.builder().id(orgId).currency(currency).build()));
    }

    @Test
    void aPriceInAnotherCurrencyIsRefused() {
        organizationWithCurrency("PHP");
        assertThatThrownBy(() -> regionService.requireOrganizationCurrency(orgId, "USD"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("PHP");
    }

    @Test
    void aPriceInTheOrganizationsCurrencyIsAccepted() {
        organizationWithCurrency("PHP");
        assertThatCode(() -> regionService.requireOrganizationCurrency(orgId, "PHP")).doesNotThrowAnyException();
    }

    @Test
    void anOrganizationWithoutACurrencyYet_hasNothingToCheck() {
        organizationWithCurrency(null);
        assertThatCode(() -> regionService.requireOrganizationCurrency(orgId, "USD")).doesNotThrowAnyException();
    }
}
