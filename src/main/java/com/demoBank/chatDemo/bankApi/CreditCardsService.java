package com.demoBank.chatDemo.bankApi;

import com.demoBank.chatDemo.repository.GenericMongoRepository;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CreditCardsService {
    private final GenericMongoRepository repository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public CreditCardsService(GenericMongoRepository repository) {
        this.repository = repository;
    }

    public Document getCreditCardsData(String customerID, String fromDate, String toDate, 
                                      String last4Digits, Boolean includeTransactions) throws IOException {
        Document res = repository.findById("creditCards", customerID);
        
        if (res == null) {
            return res;
        }

        Document data = res.get("data", Document.class);
        if (data == null) {
            return res;
        }

        @SuppressWarnings("unchecked")
        List<Document> cards = data.getList("cards", Document.class);
        if (cards == null) {
            return res;
        }

        // Filter by last 4 digits if provided and not empty
        if (last4Digits != null && !last4Digits.trim().isEmpty()) {
            filterByLast4Digits(cards, last4Digits.trim());
        }

        // Balances are always included, transactions are optional
        // Default includeTransactions to true if null
        boolean includeTrans = (includeTransactions != null) ? includeTransactions : true;
        
        for (Document cardDoc : cards) {
            if (!includeTrans) {
                // Remove transactions if not included
                cardDoc.remove("transactions");
                cardDoc.remove("transactionsSummary");
            } else {
                // Filter transactions by date if transactions are included
                filterTransactionsByDate(cardDoc, fromDate, toDate);
            }
        }

        return res;
    }

    private void filterByLast4Digits(List<Document> cards, String last4Digits) {
        List<Document> cardsToRemove = cards.stream()
                .filter(cardDoc -> {
                    Document card = cardDoc.get("card", Document.class);
                    if (card == null) {
                        return true;
                    }
                    String maskedPan = card.getString("maskedPan");
                    if (maskedPan == null) {
                        return true;
                    }
                    // Extract last 4 digits from maskedPan (format: "**** **** **** 5512")
                    String[] parts = maskedPan.trim().split("\\s+");
                    if (parts.length == 0) {
                        return true;
                    }
                    String cardLast4 = parts[parts.length - 1];
                    return !cardLast4.equals(last4Digits);
                })
                .collect(Collectors.toList());

        cards.removeAll(cardsToRemove);
    }

    private void filterTransactionsByDate(Document cardDoc, String fromDate, String toDate) {
        @SuppressWarnings("unchecked")
        List<Document> transactions = cardDoc.getList("transactions", Document.class);
        if (transactions == null || transactions.isEmpty()) {
            return;
        }

        LocalDate from = LocalDate.parse(fromDate, DATE_FORMATTER);
        LocalDate to = LocalDate.parse(toDate, DATE_FORMATTER);

        List<Document> filteredTransactions = transactions.stream()
                .filter(transaction -> {
                    // Use transactionDate for filtering (or postingDate if transactionDate is null)
                    String dateStr = transaction.getString("transactionDate");
                    if (dateStr == null) {
                        dateStr = transaction.getString("postingDate");
                    }
                    if (dateStr == null) {
                        // Include transactions without date
                        return true;
                    }
                    try {
                        LocalDate transactionDate = LocalDate.parse(dateStr, DATE_FORMATTER);
                        return !transactionDate.isBefore(from) && !transactionDate.isAfter(to);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());

        cardDoc.put("transactions", filteredTransactions);
        
        // Update transaction count in summary if it exists
        Document transactionsSummary = cardDoc.get("transactionsSummary", Document.class);
        if (transactionsSummary != null) {
            transactionsSummary.put("transactionCount", filteredTransactions.size());
        }
    }
}

