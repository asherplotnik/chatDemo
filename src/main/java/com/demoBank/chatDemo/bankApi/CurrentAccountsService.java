package com.demoBank.chatDemo.bankApi;

import com.demoBank.chatDemo.repository.GenericMongoRepository;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CurrentAccountsService {
    private final GenericMongoRepository repository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public CurrentAccountsService(GenericMongoRepository repository) {
        this.repository = repository;
    }

    public Document getCurrentAccountsData(String customerID, String fromDate, String toDate, 
                                           List<String> accountList, Boolean includeTransactions) throws IOException {
        Document res = repository.findById("currentAccountTransactions", customerID);
        
        if (res == null) {
            return res;
        }

        Document data = res.get("data", Document.class);
        if (data == null) {
            return res;
        }

        @SuppressWarnings("unchecked")
        List<Document> accounts = data.getList("accounts", Document.class);
        if (accounts == null) {
            return res;
        }

        // Filter accounts if accountList is provided and not empty
        if (accountList != null && !accountList.isEmpty()) {
            filterAccounts(accounts, accountList);
        }

        // Balances are always included, transactions are optional
        // Default includeTransactions to true if null
        boolean includeTrans = (includeTransactions != null) ? includeTransactions : true;
        
        for (Document accountDoc : accounts) {
            if (!includeTrans) {
                // Remove transactions if not included
                accountDoc.remove("transactions");
                accountDoc.remove("transactionsSummary");
                accountDoc.remove("pagination");
            } else {
                // Filter transactions by date if transactions are included
                filterTransactionsByDate(accountDoc, fromDate, toDate);
            }
        }

        return res;
    }

    private void filterAccounts(List<Document> accounts, List<String> accountList) {
        Set<String> accountIdSet = accountList.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        List<Document> accountsToRemove = accounts.stream()
                .filter(accountDoc -> {
                    Document account = accountDoc.get("account", Document.class);
                    if (account == null) {
                        return true;
                    }
                    String accountId = account.getString("accountId");
                    return accountId == null || !accountIdSet.contains(accountId.toLowerCase());
                })
                .collect(Collectors.toList());

        accounts.removeAll(accountsToRemove);
    }

    private void filterTransactionsByDate(Document accountDoc, String fromDate, String toDate) {
        @SuppressWarnings("unchecked")
        List<Document> transactions = accountDoc.getList("transactions", Document.class);
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
                        return !bookingDate.isBefore(from) && !bookingDate.isAfter(to);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());

        accountDoc.put("transactions", filteredTransactions);
        
        // Update transaction count in summary if it exists
        Document transactionsSummary = accountDoc.get("transactionsSummary", Document.class);
        if (transactionsSummary != null) {
            transactionsSummary.put("transactionCount", filteredTransactions.size());
        }
    }
}
