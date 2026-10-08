package com.example.printagent;

import com.example.printagent.config.AgentConfiguration;
import com.example.printagent.printer.HostPrintServiceProvider;
import com.example.printagent.printer.PrinterDiscovery;
import com.example.printagent.runtime.PrintAgentRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PrintAgentApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrintAgentApplication.class);

    private PrintAgentApplication() {
    }

    public static void main(String[] args) {
        if (args.length == 1 && "--list-printers".equals(args[0])) {
            listPrinters();
            return;
        }
        if (args.length != 0) {
            throw new IllegalArgumentException("Supported arguments: --list-printers");
        }
        AgentConfiguration configuration = AgentConfiguration.fromEnvironment(System.getenv());
        LOGGER.info("Starting PrintDesk agent {} with backend {}.", configuration.agentCode(), configuration.backendBaseUrl());
        new PrintAgentRuntime(
                configuration,
                new PrinterDiscovery(new HostPrintServiceProvider()))
                .run();
    }

    private static void listPrinters() {
        var printers = new PrinterDiscovery(new HostPrintServiceProvider()).discover();
        if (printers.isEmpty()) {
            LOGGER.warn("No compatible host printers were discovered.");
            return;
        }
        printers.forEach(printer -> LOGGER.info(
                "Printer discovered: name={}, color={}, duplex={}, maxCopies={}, paperSizes={}",
                printer.systemName(),
                printer.colorSupported(),
                printer.duplexSupported(),
                printer.maximumCopies(),
                printer.paperSizes()));
    }
}
