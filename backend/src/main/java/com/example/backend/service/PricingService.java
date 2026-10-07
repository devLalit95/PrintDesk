package com.example.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.example.backend.entity.PrintRateEntity;
import com.example.backend.entity.PrintType;
import com.example.backend.repository.PrintRateRepository;
import com.example.backend.service.pricing.InvalidPricingRequestException;
import com.example.backend.service.pricing.PriceQuote;
import com.example.backend.service.pricing.PricingUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PricingService {

    private static final String SUPPORTED_CURRENCY = "INR";
    private static final int MONEY_SCALE = 2;

    private final PrintRateRepository rateRepository;

    public PricingService(PrintRateRepository rateRepository) {
        this.rateRepository = rateRepository;
    }

    @Transactional(readOnly = true)
    public PriceQuote quote(PrintType printType, int documentPages, int copies) {
        if (printType == null) {
            throw new InvalidPricingRequestException("A print type is required.");
        }
        if (documentPages < 1 || copies < 1) {
            throw new InvalidPricingRequestException("Page count and copy count must be positive.");
        }

        int totalPages;
        try {
            totalPages = Math.multiplyExact(documentPages, copies);
        } catch (ArithmeticException exception) {
            throw new InvalidPricingRequestException("The total page count exceeds the supported range.");
        }

        PrintRateEntity rate = rateRepository.findByPrintTypeAndActiveTrue(printType)
                .orElseThrow(() -> new PricingUnavailableException(
                        "No active print rate is configured for the requested print type."));
        if (rate.getPricePerPage() == null
                || rate.getPricePerPage().signum() < 0
                || !SUPPORTED_CURRENCY.equals(rate.getCurrency())) {
            throw new PricingUnavailableException(
                    "The configured print rate is invalid or uses an unsupported currency.");
        }

        BigDecimal totalAmount = rate.getPricePerPage()
                .multiply(BigDecimal.valueOf(totalPages))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new PriceQuote(
                printType,
                documentPages,
                copies,
                totalPages,
                rate.getPricePerPage(),
                totalAmount,
                rate.getCurrency());
    }
}
