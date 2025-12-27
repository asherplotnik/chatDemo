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
public class MortgageService {
    private final GenericMongoRepository repository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public MortgageService(GenericMongoRepository repository) {
        this.repository = repository;
    }

    public Document getMortgageData(String customerID, String fromDate, String toDate, String nicknameFilter, Boolean includeTransactions) throws IOException {
        Document res = repository.findById("mortgages", customerID);
        
        if (res == null) {
            return res;
        }

        Document data = res.get("data", Document.class);
        if (data == null) {
            return res;
        }

        @SuppressWarnings("unchecked")
        List<Document> mortgages = data.getList("mortgages", Document.class);
        if (mortgages == null) {
            return res;
        }

        // Filter by nickname if nicknameFilter is provided and not empty
        if (nicknameFilter != null && !nicknameFilter.trim().isEmpty()) {
            filterByNickname(mortgages, nicknameFilter);
        }

        // Balances are always included, transactions are optional
        // Default includeTransactions to true if null
        boolean includeTrans = (includeTransactions != null) ? includeTransactions : true;
        
        for (Document mortgageDoc : mortgages) {
            if (!includeTrans) {
                // Remove transactions if not included
                mortgageDoc.remove("transactions");
                mortgageDoc.remove("transactionsSummary");
            } else {
                // Filter transactions by date range if transactions are included
                filterTransactionsByDate(mortgageDoc, fromDate, toDate);
            }
        }

        return res;
    }

    private void filterByNickname(List<Document> mortgages, String nicknameFilter) {
        String filterLower = nicknameFilter.toLowerCase().trim();
        
        List<Document> mortgagesToRemove = mortgages.stream()
                .filter(mortgageDoc -> {
                    Document mortgage = mortgageDoc.get("mortgage", Document.class);
                    if (mortgage == null) {
                        return true;
                    }
                    String nickname = mortgage.getString("nickname");
                    if (nickname == null) {
                        return true;
                    }
                    String nicknameLower = nickname.toLowerCase();
                    // Match if equals or contains (case-insensitive)
                    return !nicknameLower.equals(filterLower) && !nicknameLower.contains(filterLower);
                })
                .collect(Collectors.toList());

        mortgages.removeAll(mortgagesToRemove);
    }

    private void filterTransactionsByDate(Document mortgageDoc, String fromDate, String toDate) {
        @SuppressWarnings("unchecked")
        List<Document> transactions = mortgageDoc.getList("transactions", Document.class);
        if (transactions == null || transactions.isEmpty()) {
            return;
        }

        LocalDate from = LocalDate.parse(fromDate, DATE_FORMATTER);
        LocalDate to = LocalDate.parse(toDate, DATE_FORMATTER);

        List<Document> filteredTransactions = transactions.stream()
                .filter(transaction -> {
                    String bookingDateStr = transaction.getString("bookingDate");
                    if (bookingDateStr == null) {
                        // Include pending transactions (no booking date)
                        return true;
                    }
                    try {
                        LocalDate bookingDate = LocalDate.parse(bookingDateStr, DATE_FORMATTER);
                        // Include transactions between fromDate and toDate (inclusive)
                        return !bookingDate.isBefore(from) && !bookingDate.isAfter(to);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());

        mortgageDoc.put("transactions", filteredTransactions);
        
        // Update transaction count in summary if it exists
        Document transactionsSummary = mortgageDoc.get("transactionsSummary", Document.class);
        if (transactionsSummary != null) {
            transactionsSummary.put("transactionCount", filteredTransactions.size());
        }
    }
}
