package com.billing.invoicehub.service;

import java.io.IOException;

public interface AIProvider {
    /**
     * Extracts structured quotation JSON from the raw PDF text.
     */
    String extractQuotation(String pdfText) throws IOException, InterruptedException;
}
