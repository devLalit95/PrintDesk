package com.example.backend.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.example.backend.dto.order.CreatePrintOrderRequest;
import com.example.backend.dto.order.CreatePrintOrderResponse;
import com.example.backend.dto.order.PriceEstimateRequest;
import com.example.backend.dto.order.PrintOrderStatusResponse;
import com.example.backend.entity.DocumentEntity;
import com.example.backend.entity.PrintOrderEntity;
import com.example.backend.entity.PrintOrderStatus;
import com.example.backend.entity.PrintType;
import com.example.backend.repository.DocumentRepository;
import com.example.backend.repository.PrintOrderRepository;
import com.example.backend.service.document.DocumentNotFoundException;
import com.example.backend.service.pricing.PriceQuote;
import com.example.backend.service.order.InvalidPrintOrderRequestException;
import com.example.backend.service.order.OrderTokenGenerationException;
import com.example.backend.service.order.OrderTokenGenerator;
import com.example.backend.service.order.PrintOrderNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrintOrderService {

    private static final int TOKEN_GENERATION_ATTEMPTS = 5;
    private static final BigDecimal MAX_ORDER_AMOUNT = new BigDecimal("9999999999.99");

    private final DocumentRepository documentRepository;
    private final PrintOrderRepository orderRepository;
    private final PricingService pricingService;
    private final OrderTokenGenerator tokenGenerator;

    public PrintOrderService(
            DocumentRepository documentRepository,
            PrintOrderRepository orderRepository,
            PricingService pricingService,
            OrderTokenGenerator tokenGenerator) {
        this.documentRepository = documentRepository;
        this.orderRepository = orderRepository;
        this.pricingService = pricingService;
        this.tokenGenerator = tokenGenerator;
    }

    @Transactional
    public CreatePrintOrderResponse createOrder(CreatePrintOrderRequest request) {
        validateRequest(request);

        DocumentEntity document = findDocument(request.documentId());
        PriceQuote quote = quoteForDocument(document, request.printType(), request.copies());

        String token = generateUniqueToken();
        PrintOrderEntity order = new PrintOrderEntity(
                token,
                document,
                quote.documentPages(),
                quote.printType(),
                quote.copies(),
                quote.pricePerPage(),
                quote.totalPages(),
                quote.totalAmount(),
                normalizePaperSize(request.paperSize()),
                normalizeOrientation(request.orientation()),
                null,
                request.doubleSided());

        orderRepository.save(order);
        return new CreatePrintOrderResponse(
                order.getToken(),
                document.getOriginalFileName(),
                order.getPageCount(),
                order.getPrintType(),
                order.getCopies(),
                order.getTotalPages(),
                order.getPricePerPage(),
                order.getTotalAmount(),
                quote.currency(),
                order.getStatus());
    }

    @Transactional(readOnly = true)
    public PriceQuote estimatePrice(PriceEstimateRequest request) {
        if (request == null
                || request.documentId() == null
                || request.printType() == null
                || request.copies() < 1) {
            throw new InvalidPrintOrderRequestException(
                    "A document, print type, and positive copy count are required.");
        }
        return quoteForDocument(
                findDocument(request.documentId()),
                request.printType(),
                request.copies());
    }

    @Transactional(readOnly = true)
    public PrintOrderStatusResponse getStatusByToken(String token) {
        if (token == null) {
            throw new PrintOrderNotFoundException();
        }
        String normalizedToken = token.trim().toUpperCase(Locale.ROOT);
        if (!OrderTokenGenerator.isValid(normalizedToken)) {
            throw new PrintOrderNotFoundException();
        }

        PrintOrderEntity order = orderRepository.findByToken(normalizedToken)
                .orElseThrow(PrintOrderNotFoundException::new);
        return new PrintOrderStatusResponse(
                order.getToken(),
                order.getStatus(),
                order.getPrintType(),
                order.getPageCount(),
                order.getCopies(),
                order.getTotalPages(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getPrintedAt());
    }

    @Transactional
    public PrintOrderEntity transitionStatus(UUID orderId, PrintOrderStatus nextStatus) {
        if (orderId == null) {
            throw new PrintOrderNotFoundException();
        }
        PrintOrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(PrintOrderNotFoundException::new);
        order.transitionTo(nextStatus, Instant.now());
        return order;
    }

    private String generateUniqueToken() {
        for (int attempt = 0; attempt < TOKEN_GENERATION_ATTEMPTS; attempt++) {
            String token = tokenGenerator.generate();
            if (OrderTokenGenerator.isValid(token) && !orderRepository.existsByToken(token)) {
                return token;
            }
        }
        throw new OrderTokenGenerationException();
    }

    private DocumentEntity findDocument(UUID documentId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(DocumentNotFoundException::new);
        if (document.getPageCount() == null || document.getPageCount() < 1) {
            throw new InvalidPrintOrderRequestException(
                    "The uploaded document does not have a supported page count.");
        }
        return document;
    }

    private PriceQuote quoteForDocument(DocumentEntity document, PrintType printType, int copies) {
        PriceQuote quote = pricingService.quote(printType, document.getPageCount(), copies);
        if (quote.totalAmount().compareTo(MAX_ORDER_AMOUNT) > 0) {
            throw new InvalidPrintOrderRequestException(
                    "The calculated order amount exceeds the supported range.");
        }
        return quote;
    }

    private static void validateRequest(CreatePrintOrderRequest request) {
        if (request == null
                || request.documentId() == null
                || request.printType() == null
                || request.copies() < 1) {
            throw new InvalidPrintOrderRequestException(
                    "A document, print type, and positive copy count are required.");
        }
        normalizePaperSize(request.paperSize());
        normalizeOrientation(request.orientation());
    }

    private static String normalizePaperSize(String paperSize) {
        if (paperSize == null) {
            throw new InvalidPrintOrderRequestException("A supported paper size is required.");
        }
        return switch (paperSize.trim().toUpperCase(Locale.ROOT)) {
            case "A4" -> "A4";
            case "A3" -> "A3";
            case "LETTER" -> "Letter";
            case "LEGAL" -> "Legal";
            default -> throw new InvalidPrintOrderRequestException("Choose a supported paper size.");
        };
    }

    private static String normalizeOrientation(String orientation) {
        if (orientation == null) {
            throw new InvalidPrintOrderRequestException("A supported orientation is required.");
        }
        return switch (orientation.trim().toLowerCase(Locale.ROOT)) {
            case "portrait" -> "portrait";
            case "landscape" -> "landscape";
            default -> throw new InvalidPrintOrderRequestException("Choose a supported orientation.");
        };
    }
}
