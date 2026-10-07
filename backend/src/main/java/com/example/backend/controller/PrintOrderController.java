package com.example.backend.controller;

import java.net.URI;

import com.example.backend.dto.order.CreatePrintOrderRequest;
import com.example.backend.dto.order.CreatePrintOrderResponse;
import com.example.backend.dto.order.PriceEstimateRequest;
import com.example.backend.dto.order.PriceEstimateResponse;
import com.example.backend.dto.order.PrintOrderStatusResponse;
import com.example.backend.service.PrintOrderService;
import com.example.backend.service.pricing.PriceQuote;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/print-orders")
public class PrintOrderController {

    private final PrintOrderService orderService;

    public PrintOrderController(PrintOrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/estimate")
    public ResponseEntity<PriceEstimateResponse> estimate(@Valid @RequestBody PriceEstimateRequest request) {
        PriceQuote quote = orderService.estimatePrice(request);
        return ResponseEntity.ok(new PriceEstimateResponse(
                quote.printType(),
                quote.documentPages(),
                quote.copies(),
                quote.totalPages(),
                quote.pricePerPage(),
                quote.totalAmount(),
                quote.currency()));
    }

    @PostMapping
    public ResponseEntity<CreatePrintOrderResponse> createOrder(
            @Valid @RequestBody CreatePrintOrderRequest request) {
        CreatePrintOrderResponse response = orderService.createOrder(request);
        return ResponseEntity.created(URI.create("/api/print-orders/" + response.token()))
                .body(response);
    }

    @GetMapping("/{token}")
    public ResponseEntity<PrintOrderStatusResponse> getStatus(@PathVariable String token) {
        return ResponseEntity.ok(orderService.getStatusByToken(token));
    }
}
