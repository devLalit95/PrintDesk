package com.example.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.example.backend.dto.order.CreatePrintOrderRequest;
import com.example.backend.entity.DocumentEntity;
import com.example.backend.entity.PrintOrderEntity;
import com.example.backend.entity.PrintOrderStatus;
import com.example.backend.entity.PrintType;
import com.example.backend.repository.DocumentRepository;
import com.example.backend.repository.PrintOrderRepository;
import com.example.backend.service.document.DocumentNotFoundException;
import com.example.backend.service.order.InvalidPrintOrderRequestException;
import com.example.backend.service.order.OrderTokenGenerator;
import com.example.backend.service.order.OrderTokenGenerationException;
import com.example.backend.service.order.PrintOrderNotFoundException;
import com.example.backend.service.pricing.PriceQuote;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PrintOrderServiceTest {

    private final DocumentRepository documentRepository = org.mockito.Mockito.mock(DocumentRepository.class);
    private final PrintOrderRepository orderRepository = org.mockito.Mockito.mock(PrintOrderRepository.class);
    private final PricingService pricingService = org.mockito.Mockito.mock(PricingService.class);
    private final OrderTokenGenerator tokenGenerator = org.mockito.Mockito.mock(OrderTokenGenerator.class);
    private final PrintOrderService service = new PrintOrderService(
            documentRepository,
            orderRepository,
            pricingService,
            tokenGenerator);

    @Test
    void createsOrderUsingPersistedDocumentPagesAndServerCalculatedQuote() {
        UUID documentId = UUID.randomUUID();
        DocumentEntity document = document(4);
        CreatePrintOrderRequest request = request(documentId, 2);
        PriceQuote quote = new PriceQuote(
                PrintType.COLOR,
                4,
                2,
                8,
                new BigDecimal("3.25"),
                new BigDecimal("26.00"),
                "INR");
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(pricingService.quote(PrintType.COLOR, 4, 2)).thenReturn(quote);
        when(tokenGenerator.generate()).thenReturn("ABCDEFGHJKLM");
        when(orderRepository.existsByToken("ABCDEFGHJKLM")).thenReturn(false);
        when(orderRepository.save(any(PrintOrderEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.createOrder(request);

        ArgumentCaptor<PrintOrderEntity> orderCaptor = ArgumentCaptor.forClass(PrintOrderEntity.class);
        verify(orderRepository).save(orderCaptor.capture());
        PrintOrderEntity savedOrder = orderCaptor.getValue();
        assertEquals("ABCDEFGHJKLM", savedOrder.getToken());
        assertEquals(document, savedOrder.getDocument());
        assertEquals(4, savedOrder.getPageCount());
        assertEquals(8, savedOrder.getTotalPages());
        assertEquals(new BigDecimal("3.25"), savedOrder.getPricePerPage());
        assertEquals(new BigDecimal("26.00"), savedOrder.getTotalAmount());
        assertEquals("A4", savedOrder.getPaperSize());
        assertEquals("landscape", savedOrder.getOrientation());
        assertEquals(PrintOrderStatus.PENDING, savedOrder.getStatus());
        assertEquals("ABCDEFGHJKLM", created.token());
        assertEquals("upload.pdf", created.fileName());
        assertEquals(new BigDecimal("26.00"), created.totalAmount());
        verify(pricingService).quote(PrintType.COLOR, 4, 2);
    }

    @Test
    void retriesTokensAlreadyInUseBeforeSavingOrder() {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document(1)));
        when(pricingService.quote(PrintType.COLOR, 1, 1)).thenReturn(new PriceQuote(
                PrintType.COLOR,
                1,
                1,
                1,
                new BigDecimal("1.00"),
                new BigDecimal("1.00"),
                "INR"));
        when(tokenGenerator.generate()).thenReturn("ABCDEFGHJKLM", "BCDEFGHJKLMN");
        when(orderRepository.existsByToken("ABCDEFGHJKLM")).thenReturn(true);
        when(orderRepository.existsByToken("BCDEFGHJKLMN")).thenReturn(false);
        when(orderRepository.save(any(PrintOrderEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.createOrder(request(documentId, 1));

        assertEquals("BCDEFGHJKLMN", created.token());
        verify(orderRepository).existsByToken("ABCDEFGHJKLM");
        verify(orderRepository).existsByToken("BCDEFGHJKLMN");
    }

    @Test
    void failsExplicitlyWhenEveryGeneratedTokenIsAlreadyInUse() {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document(1)));
        when(pricingService.quote(PrintType.COLOR, 1, 1)).thenReturn(new PriceQuote(
                PrintType.COLOR,
                1,
                1,
                1,
                new BigDecimal("1.00"),
                new BigDecimal("1.00"),
                "INR"));
        when(tokenGenerator.generate()).thenReturn("ABCDEFGHJKLM");
        when(orderRepository.existsByToken("ABCDEFGHJKLM")).thenReturn(true);

        assertThrows(OrderTokenGenerationException.class, () -> service.createOrder(request(documentId, 1)));

        verify(orderRepository, never()).save(any(PrintOrderEntity.class));
    }

    @Test
    void rejectsMissingDocumentWithoutCalculatingOrCreatingOrder() {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        assertThrows(DocumentNotFoundException.class, () -> service.createOrder(request(documentId, 1)));

        verify(pricingService, never()).quote(any(), anyInt(), anyInt());
    }

    @Test
    void looksUpPublicStatusUsingNormalizedToken() {
        PrintOrderEntity order = new PrintOrderEntity(
                "ABCDEFGHJKLM",
                document(3),
                3,
                PrintType.COLOR,
                2,
                new BigDecimal("2.50"),
                6,
                new BigDecimal("15.00"),
                "A4",
                "portrait",
                null,
                false);
        when(orderRepository.findByToken("ABCDEFGHJKLM")).thenReturn(Optional.of(order));

        var response = service.getStatusByToken("  abcdefghjklm ");

        assertEquals("ABCDEFGHJKLM", response.token());
        assertEquals(PrintOrderStatus.PENDING, response.status());
        assertEquals(PrintType.COLOR, response.printType());
        assertEquals(6, response.totalPages());
        assertEquals(new BigDecimal("15.00"), response.totalAmount());
        verify(orderRepository).findByToken("ABCDEFGHJKLM");
    }

    @Test
    void hidesMalformedAndUnknownPublicTokensAsNotFound() {
        assertThrows(PrintOrderNotFoundException.class, () -> service.getStatusByToken("not-a-token"));
        when(orderRepository.findByToken("ABCDEFGHJKLM")).thenReturn(Optional.empty());
        assertThrows(
                PrintOrderNotFoundException.class,
                () -> service.getStatusByToken("ABCDEFGHJKLM"));
    }

    @Test
    void rejectsCalculatedAmountsThatCannotFitTheOrderSchema() {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document(1)));
        when(pricingService.quote(PrintType.COLOR, 1, 1)).thenReturn(new PriceQuote(
                PrintType.COLOR,
                1,
                1,
                1,
                new BigDecimal("10000000000.00"),
                new BigDecimal("10000000000.00"),
                "INR"));

        assertThrows(InvalidPrintOrderRequestException.class, () -> service.createOrder(request(documentId, 1)));

        verify(orderRepository, never()).save(any(PrintOrderEntity.class));
    }

    @Test
    void rejectsDocumentsWithoutAUsableServerDeterminedPageCount() {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document(null)));

        assertThrows(InvalidPrintOrderRequestException.class, () -> service.createOrder(request(documentId, 1)));

        verify(pricingService, never()).quote(any(), anyInt(), anyInt());
        verify(orderRepository, never()).save(any(PrintOrderEntity.class));
    }

    @Test
    void rejectsUnsupportedPrintPreferences() {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document(1)));

        assertThrows(
                InvalidPrintOrderRequestException.class,
                () -> service.createOrder(new CreatePrintOrderRequest(
                        documentId,
                        PrintType.COLOR,
                        1,
                        "A0",
                        "portrait",
                        false)));

        verify(pricingService, never()).quote(any(), anyInt(), anyInt());
    }

    private static CreatePrintOrderRequest request(UUID documentId, int copies) {
        return new CreatePrintOrderRequest(
                documentId,
                PrintType.COLOR,
                copies,
                "A4",
                "LANDSCAPE",
                false);
    }

    private static DocumentEntity document(Integer pageCount) {
        return new DocumentEntity(
                "upload.pdf",
                "generated/storage-key",
                "application/pdf",
                100,
                pageCount,
                "a".repeat(64));
    }
}
