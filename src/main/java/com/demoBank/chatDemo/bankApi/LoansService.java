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
public class LoansService {
    private final GenericMongoRepository repository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public LoansService(GenericMongoRepository repository) {
        this.repository = repository;
    }

    public Document getLoansData(String customerID, String fromDate, String toDate, String nicknameFilter, Boolean includeTransactions) throws IOException {
        Document res = repository.findById("loans", customerID);
        
        if (res == null) {
            return res;
        }

        Document data = res.get("data", Document.class);
        if (data == null) {
            return res;
        }

        @SuppressWarnings("unchecked")
        List<Document> loans = data.getList("loans", Document.class);
        if (loans == null) {
            return res;
        }

        // Filter by nickname if nicknameFilter is provided and not empty
        if (nicknameFilter != null && !nicknameFilter.trim().isEmpty()) {
            filterByNickname(loans, nicknameFilter);
        }

        // Balances are always included, transactions are optional
        // Default includeTransactions to true if null
        boolean includeTrans = (includeTransactions != null) ? includeTransactions : true;
        
        for (Document loanDoc : loans) {
            if (!includeTrans) {
                // Remove transactions if not included
                loanDoc.remove("transactions");
                loanDoc.remove("transactionsSummary");
            } else {
                // Filter transactions by date range if transactions are included
                filterTransactionsByDate(loanDoc, fromDate, toDate);
            }
        }

        return res;
    }

    private void filterByNickname(List<Document> loans, String nicknameFilter) {
        String filterLower = nicknameFilter.toLowerCase().trim();
        
        List<Document> loansToRemove = loans.stream()
                .filter(loanDoc -> {
                    Document loan = loanDoc.get("loan", Document.class);
                    if (loan == null) {
                        return true;
                    }
                    String nickname = loan.getString("nickname");
                    if (nickname == null) {
                        return true;
                    }
                    String nicknameLower = nickname.toLowerCase();
                    // Match if equals or contains (case-insensitive)
                    return !nicknameLower.equals(filterLower) && !nicknameLower.contains(filterLower);
                })
                .collect(Collectors.toList());

        loans.removeAll(loansToRemove);
    }

    private void filterTransactionsByDate(Document loanDoc, String fromDate, String toDate) {
        @SuppressWarnings("unchecked")
        List<Document> transactions = loanDoc.getList("transactions", Document.class);
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

        loanDoc.put("transactions", filteredTransactions);
        
        // Update transaction count in summary if it exists
        Document transactionsSummary = loanDoc.get("transactionsSummary", Document.class);
        if (transactionsSummary != null) {
            transactionsSummary.put("transactionCount", filteredTransactions.size());
        }
    }
}
