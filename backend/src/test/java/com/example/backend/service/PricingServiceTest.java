package com.example.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import com.example.backend.entity.PrintRateEntity;
import com.example.backend.entity.PrintType;
import com.example.backend.repository.PrintRateRepository;
import com.example.backend.service.pricing.InvalidPricingRequestException;
import com.example.backend.service.pricing.PricingUnavailableException;
import com.example.backend.service.pricing.PriceQuote;
import org.junit.jupiter.api.Test;

class PricingServiceTest {

    private final PrintRateRepository rateRepository = org.mockito.Mockito.mock(PrintRateRepository.class);
    private final PricingService pricingService = new PricingService(rateRepository);

    @Test
    void calculatesPriceFromPersistedRateAndTotalPages() {
        when(rateRepository.findByPrintTypeAndActiveTrue(PrintType.BLACK_AND_WHITE))
                .thenReturn(Optional.of(new PrintRateEntity(
                        PrintType.BLACK_AND_WHITE,
                        new BigDecimal("2.00"),
                        "INR")));

        PriceQuote quote = pricingService.quote(PrintType.BLACK_AND_WHITE, 5, 2);

        assertEquals(10, quote.totalPages());
        assertEquals(new BigDecimal("2.00"), quote.pricePerPage());
        assertEquals(new BigDecimal("20.00"), quote.totalAmount());
        assertEquals("INR", quote.currency());
        verify(rateRepository).findByPrintTypeAndActiveTrue(PrintType.BLACK_AND_WHITE);
    }

    @Test
    void roundsTheServerCalculatedOrderTotalToTwoPlacesUsingHalfUp() {
        when(rateRepository.findByPrintTypeAndActiveTrue(PrintType.COLOR))
                .thenReturn(Optional.of(new PrintRateEntity(
                        PrintType.COLOR,
                        new BigDecimal("1.005"),
                        "INR")));

        PriceQuote quote = pricingService.quote(PrintType.COLOR, 1, 1);

        assertEquals(new BigDecimal("1.01"), quote.totalAmount());
    }

    @Test
    void rejectsNonPositivePagesAndCopiesBeforeLookingUpRates() {
        assertThrows(
                InvalidPricingRequestException.class,
                () -> pricingService.quote(PrintType.BLACK_AND_WHITE, 0, 1));
        assertThrows(
                InvalidPricingRequestException.class,
                () -> pricingService.quote(PrintType.BLACK_AND_WHITE, 1, 0));
    }

    @Test
    void rejectsTotalPageCountsOutsideThePersistedIntegerRange() {
        assertThrows(
                InvalidPricingRequestException.class,
                () -> pricingService.quote(PrintType.BLACK_AND_WHITE, Integer.MAX_VALUE, 2));
    }

    @Test
    void failsClearlyWhenNoActiveRateIsConfigured() {
        when(rateRepository.findByPrintTypeAndActiveTrue(PrintType.COLOR)).thenReturn(Optional.empty());

        assertThrows(
                PricingUnavailableException.class,
                () -> pricingService.quote(PrintType.COLOR, 1, 1));
    }

    @Test
    void rejectsNonInrRatesBecauseOrdersCurrentlyPersistInrOnly() {
        when(rateRepository.findByPrintTypeAndActiveTrue(PrintType.COLOR))
                .thenReturn(Optional.of(new PrintRateEntity(
                        PrintType.COLOR,
                        new BigDecimal("3.00"),
                        "USD")));

        assertThrows(
                PricingUnavailableException.class,
                () -> pricingService.quote(PrintType.COLOR, 1, 1));
    }
}
