package com.minipay.mpps.common;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class TransactionReferenceGenerator {
    //Prefix
    private static final String PREFIX = "TXN";

    /**
     * Generates a unique transaction reference.
     *
     * The reference follows the structure: TXN-yyyyMMddHHmmss-random,
     * where random is 6-10 characters.
     *
     * Example: TXN-20260322-8F3K2L9A
     *
     * @return the generated transaction reference
     */
    public String generateTransactionReference() {

        //Exact date
        OffsetDateTime now = OffsetDateTime.now();
        String formattedDate = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        //Get the random part
        String randomPart = UUID.randomUUID().toString().substring(0, 8);

        //Return generated reference
        return String.format("%s-%s-%s", PREFIX, formattedDate, randomPart);
    }
}
