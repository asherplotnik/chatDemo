package com.demoBank.chatDemo.orchestrator.service;

import com.demoBank.chatDemo.orchestrator.model.*;
import com.demoBank.chatDemo.gateway.model.RequestContext;
import com.demoBank.chatDemo.orchestrator.model.OrchestrationState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for normalizing API responses to canonical internal models.
 * 
 * Converts different API response formats into a unified structure
 * for downstream processing (computation, drafting).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NormalizationService {
    
    /**
     * Normalizes fetched API data into canonical internal models.
     * 
     * @param state Current orchestration state with fetchedData
     * @param requestContext Request context
     * @return Updated orchestration state with normalizedData
     */
    public OrchestrationState normalize(OrchestrationState state, RequestContext requestContext) {
        String correlationId = requestContext.getCorrelationId();
        log.debug("Step NORMALIZE - correlationId: {}", correlationId);
        
        Object fetchedData = state.getFetchedData();
        if (fetchedData == null) {
            log.warn("No fetched data to normalize - correlationId: {}", correlationId);
            return state;
        }
        
        try {
            List<NormalizedData> normalizedDataList = new ArrayList<>();
            
            // fetchedData is a List<Map<String, Object>> where each map contains:
            // - domain: string
            // - metric: string
            // - intent: IntentData
            // - apiResponse: Map/Object (the actual API response)
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> apiResponses = (List<Map<String, Object>>) fetchedData;
            
            for (Map<String, Object> responseWithMetadata : apiResponses) {
                String domain = (String) responseWithMetadata.get("domain");
                @SuppressWarnings("unchecked")
                Map<String, Object> apiResponse = (Map<String, Object>) responseWithMetadata.get("apiResponse");
                
                if (apiResponse == null) {
                    log.warn("Skipping null API response for domain {} - correlationId: {}", domain, correlationId);
                    continue;
                }
                
                NormalizedData normalizedData = normalizeApiResponse(domain, apiResponse, correlationId);
                if (normalizedData != null) {
                    normalizedDataList.add(normalizedData);
                }
            }
            
            state.setNormalizedData(normalizedDataList);
            log.info("Normalization completed - correlationId: {}, domains normalized: {}", 
                    correlationId, normalizedDataList.size());
            
        } catch (Exception e) {
            log.error("Error normalizing data - correlationId: {}", correlationId, e);
            // Don't fail orchestration, but log the error
        }
        
        return state;
    }
    
    /**
     * Normalizes a single API response based on domain type.
     */
    @SuppressWarnings("unchecked")
    private NormalizedData normalizeApiResponse(String domain, Map<String, Object> apiResponse, String correlationId) {
        try {
            Map<String, Object> data = (Map<String, Object>) apiResponse.get("data");
            Map<String, Object> metadata = (Map<String, Object>) apiResponse.get("metadata");
            
            if (data == null) {
                log.warn("API response missing 'data' field for domain {} - correlationId: {}", domain, correlationId);
                return null;
            }
            
            List<NormalizedEntity> entities = new ArrayList<>();
            
            switch (domain) {
                case "current-accounts":
                    entities = normalizeCurrentAccounts(data, correlationId);
                    break;
                case "credit-cards":
                    entities = normalizeCreditCards(data, correlationId);
                    break;
                case "loans":
                    entities = normalizeLoans(data, correlationId);
                    break;
                case "mortgages":
                    entities = normalizeMortgages(data, correlationId);
                    break;
                case "deposits":
                    entities = normalizeDeposits(data, correlationId);
                    break;
                case "securities":
                    entities = normalizeSecurities(data, correlationId);
                    break;
                case "foreign-current-accounts":
                    entities = normalizeForeignCurrentAccounts(data, correlationId);
                    break;
                default:
                    log.warn("Unknown domain for normalization: {} - correlationId: {}", domain, correlationId);
                    return null;
            }
            
            NormalizedMetadata normalizedMetadata = normalizeMetadata(metadata);
            
            return NormalizedData.builder()
                    .domain(domain)
                    .entities(entities)
                    .metadata(normalizedMetadata)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error normalizing API response for domain {} - correlationId: {}", domain, correlationId, e);
            return null;
        }
    }
    
    /**
     * Normalizes current accounts API response.
     */
    @SuppressWarnings("unchecked")
    private List<NormalizedEntity> normalizeCurrentAccounts(Map<String, Object> data, String correlationId) {
        List<NormalizedEntity> entities = new ArrayList<>();
        List<Map<String, Object>> accounts = (List<Map<String, Object>>) data.get("accounts");
        
        if (accounts == null) {
            return entities;
        }
        
        for (Map<String, Object> accountData : accounts) {
            Map<String, Object> account = (Map<String, Object>) accountData.get("account");
            Map<String, Object> balances = (Map<String, Object>) accountData.get("balances");
            List<Map<String, Object>> transactions = (List<Map<String, Object>>) accountData.get("transactions");
            Map<String, Object> transactionsSummary = (Map<String, Object>) accountData.get("transactionsSummary");
            
            NormalizedBalance balance = normalizeBalance(balances, "current-accounts");
            List<NormalizedTransaction> normalizedTransactions = normalizeTransactions(transactions, "current-accounts");
            NormalizedTransactionsSummary summary = normalizeTransactionsSummary(transactionsSummary, "current-accounts");
            
            // Preserve domain-specific fields
            Map<String, Object> domainSpecific = new HashMap<>();
            domainSpecific.put("account", account);
            domainSpecific.put("pagination", accountData.get("pagination"));
            
            NormalizedEntity entity = NormalizedEntity.builder()
                    .entityId((String) account.get("accountId"))
                    .entityType("ACCOUNT")
                    .nickname((String) account.get("nickname"))
                    .currency((String) account.get("currency"))
                    .status((String) account.get("status"))
                    .balance(balance)
                    .transactions(normalizedTransactions)
                    .transactionsSummary(summary)
                    .domainSpecific(domainSpecific)
                    .build();
            
            entities.add(entity);
        }
        
        return entities;
    }
    
    /**
     * Normalizes credit cards API response.
     */
    @SuppressWarnings("unchecked")
    private List<NormalizedEntity> normalizeCreditCards(Map<String, Object> data, String correlationId) {
        List<NormalizedEntity> entities = new ArrayList<>();
        List<Map<String, Object>> cards = (List<Map<String, Object>>) data.get("cards");
        
        if (cards == null) {
            return entities;
        }
        
        for (Map<String, Object> cardData : cards) {
            Map<String, Object> card = (Map<String, Object>) cardData.get("card");
            Map<String, Object> currentBalance = (Map<String, Object>) cardData.get("currentBalance");
            Map<String, Object> limits = (Map<String, Object>) cardData.get("limits");
            List<Map<String, Object>> transactions = (List<Map<String, Object>>) cardData.get("transactions");
            Map<String, Object> transactionsSummary = (Map<String, Object>) cardData.get("transactionsSummary");
            
            // Combine currentBalance and limits into unified balance
            NormalizedBalance balance = normalizeCreditCardBalance(currentBalance, limits);
            List<NormalizedTransaction> normalizedTransactions = normalizeTransactions(transactions, "credit-cards");
            NormalizedTransactionsSummary summary = normalizeTransactionsSummary(transactionsSummary, "credit-cards");
            
            // Preserve domain-specific fields
            Map<String, Object> domainSpecific = new HashMap<>();
            domainSpecific.put("card", card);
            domainSpecific.put("limits", limits);
            domainSpecific.put("lastStatement", cardData.get("lastStatement"));
            domainSpecific.put("pagination", cardData.get("pagination"));
            
            NormalizedEntity entity = NormalizedEntity.builder()
                    .entityId((String) card.get("cardId"))
                    .entityType("CARD")
                    .nickname((String) card.get("nickname"))
                    .currency((String) card.get("currency"))
                    .status((String) card.get("status"))
                    .balance(balance)
                    .transactions(normalizedTransactions)
                    .transactionsSummary(summary)
                    .domainSpecific(domainSpecific)
                    .build();
            
            entities.add(entity);
        }
        
        return entities;
    }
    
    /**
     * Normalizes loans API response.
     */
    @SuppressWarnings("unchecked")
    private List<NormalizedEntity> normalizeLoans(Map<String, Object> data, String correlationId) {
        List<NormalizedEntity> entities = new ArrayList<>();
        List<Map<String, Object>> loans = (List<Map<String, Object>>) data.get("loans");
        
        if (loans == null) {
            return entities;
        }
        
        for (Map<String, Object> loanData : loans) {
            Map<String, Object> loan = (Map<String, Object>) loanData.get("loan");
            Map<String, Object> balances = (Map<String, Object>) loanData.get("balances");
            List<Map<String, Object>> transactions = (List<Map<String, Object>>) loanData.get("transactions");
            Map<String, Object> transactionsSummary = (Map<String, Object>) loanData.get("transactionsSummary");
            
            NormalizedBalance balance = normalizeBalance(balances, "loans");
            List<NormalizedTransaction> normalizedTransactions = normalizeTransactions(transactions, "loans");
            NormalizedTransactionsSummary summary = normalizeTransactionsSummary(transactionsSummary, "loans");
            
            // Preserve domain-specific fields
            Map<String, Object> domainSpecific = new HashMap<>();
            domainSpecific.put("loan", loan);
            domainSpecific.put("schedule", loanData.get("schedule"));
            domainSpecific.put("features", loanData.get("features"));
            
            NormalizedEntity entity = NormalizedEntity.builder()
                    .entityId((String) loan.get("loanId"))
                    .entityType("LOAN")
                    .nickname((String) loan.get("nickname"))
                    .currency((String) loan.get("currency"))
                    .status((String) loan.get("status"))
                    .balance(balance)
                    .transactions(normalizedTransactions)
                    .transactionsSummary(summary)
                    .domainSpecific(domainSpecific)
                    .build();
            
            entities.add(entity);
        }
        
        return entities;
    }
    
    /**
     * Normalizes mortgages API response.
     */
    @SuppressWarnings("unchecked")
    private List<NormalizedEntity> normalizeMortgages(Map<String, Object> data, String correlationId) {
        List<NormalizedEntity> entities = new ArrayList<>();
        List<Map<String, Object>> mortgages = (List<Map<String, Object>>) data.get("mortgages");
        
        if (mortgages == null) {
            return entities;
        }
        
        for (Map<String, Object> mortgageData : mortgages) {
            Map<String, Object> mortgage = (Map<String, Object>) mortgageData.get("mortgage");
            Map<String, Object> balances = (Map<String, Object>) mortgageData.get("balances");
            List<Map<String, Object>> transactions = (List<Map<String, Object>>) mortgageData.get("transactions");
            Map<String, Object> transactionsSummary = (Map<String, Object>) mortgageData.get("transactionsSummary");
            
            NormalizedBalance balance = normalizeBalance(balances, "mortgages");
            List<NormalizedTransaction> normalizedTransactions = normalizeTransactions(transactions, "mortgages");
            NormalizedTransactionsSummary summary = normalizeTransactionsSummary(transactionsSummary, "mortgages");
            
            // Preserve domain-specific fields
            Map<String, Object> domainSpecific = new HashMap<>();
            domainSpecific.put("mortgage", mortgage);
            domainSpecific.put("accounts", mortgageData.get("accounts"));
            domainSpecific.put("segments", mortgageData.get("segments"));
            domainSpecific.put("features", mortgageData.get("features"));
            
            NormalizedEntity entity = NormalizedEntity.builder()
                    .entityId((String) mortgage.get("mortgageId"))
                    .entityType("MORTGAGE")
                    .nickname((String) mortgage.get("nickname"))
                    .currency((String) mortgage.get("currency"))
                    .status((String) mortgage.get("status"))
                    .balance(balance)
                    .transactions(normalizedTransactions)
                    .transactionsSummary(summary)
                    .domainSpecific(domainSpecific)
                    .build();
            
            entities.add(entity);
        }
        
        return entities;
    }
    
    /**
     * Normalizes deposits API response.
     */
    @SuppressWarnings("unchecked")
    private List<NormalizedEntity> normalizeDeposits(Map<String, Object> data, String correlationId) {
        List<NormalizedEntity> entities = new ArrayList<>();
        List<Map<String, Object>> deposits = (List<Map<String, Object>>) data.get("deposits");
        
        if (deposits == null) {
            return entities;
        }
        
        for (Map<String, Object> depositData : deposits) {
            Map<String, Object> deposit = (Map<String, Object>) depositData.get("deposit");
            Map<String, Object> balances = (Map<String, Object>) depositData.get("balances");
            List<Map<String, Object>> transactions = (List<Map<String, Object>>) depositData.get("transactions");
            Map<String, Object> transactionsSummary = (Map<String, Object>) depositData.get("transactionsSummary");
            
            NormalizedBalance balance = normalizeBalance(balances, "deposits");
            List<NormalizedTransaction> normalizedTransactions = normalizeTransactions(transactions, "deposits");
            NormalizedTransactionsSummary summary = normalizeTransactionsSummary(transactionsSummary, "deposits");
            
            // Preserve domain-specific fields
            Map<String, Object> domainSpecific = new HashMap<>();
            domainSpecific.put("deposit", deposit);
            domainSpecific.put("features", depositData.get("features"));
            
            NormalizedEntity entity = NormalizedEntity.builder()
                    .entityId((String) deposit.get("depositId"))
                    .entityType("DEPOSIT")
                    .nickname((String) deposit.get("nickname"))
                    .currency((String) deposit.get("currency"))
                    .status((String) deposit.get("status"))
                    .balance(balance)
                    .transactions(normalizedTransactions)
                    .transactionsSummary(summary)
                    .domainSpecific(domainSpecific)
                    .build();
            
            entities.add(entity);
        }
        
        return entities;
    }
    
    /**
     * Normalizes securities API response.
     */
    @SuppressWarnings("unchecked")
    private List<NormalizedEntity> normalizeSecurities(Map<String, Object> data, String correlationId) {
        List<NormalizedEntity> entities = new ArrayList<>();
        List<Map<String, Object>> accounts = (List<Map<String, Object>>) data.get("accounts");
        
        if (accounts == null) {
            return entities;
        }
        
        for (Map<String, Object> accountData : accounts) {
            Map<String, Object> account = (Map<String, Object>) accountData.get("account");
            Map<String, Object> valuation = (Map<String, Object>) accountData.get("valuation");
            
            NormalizedBalance balance = normalizeSecuritiesBalance(valuation);
            
            // Preserve domain-specific fields (securities don't have transactions in the same format)
            Map<String, Object> domainSpecific = new HashMap<>();
            domainSpecific.put("account", account);
            domainSpecific.put("valuation", valuation);
            domainSpecific.put("positionsSummary", accountData.get("positionsSummary"));
            domainSpecific.put("positions", accountData.get("positions"));
            
            NormalizedEntity entity = NormalizedEntity.builder()
                    .entityId((String) account.get("securitiesAccountId"))
                    .entityType("SECURITIES_ACCOUNT")
                    .nickname((String) account.get("nickname"))
                    .currency((String) account.get("baseCurrency"))
                    .status((String) account.get("status"))
                    .balance(balance)
                    .transactions(null) // Securities don't have transactions in the same format
                    .transactionsSummary(null) // Securities don't have transactions summary
                    .domainSpecific(domainSpecific)
                    .build();
            
            entities.add(entity);
        }
        
        return entities;
    }
    
    /**
     * Normalizes foreign current accounts API response.
     */
    @SuppressWarnings("unchecked")
    private List<NormalizedEntity> normalizeForeignCurrentAccounts(Map<String, Object> data, String correlationId) {
        List<NormalizedEntity> entities = new ArrayList<>();
        List<Map<String, Object>> accounts = (List<Map<String, Object>>) data.get("accounts");
        
        if (accounts == null) {
            return entities;
        }
        
        for (Map<String, Object> accountData : accounts) {
            Map<String, Object> account = (Map<String, Object>) accountData.get("account");
            Map<String, Object> balances = (Map<String, Object>) accountData.get("balances");
            List<Map<String, Object>> transactions = (List<Map<String, Object>>) accountData.get("transactions");
            Map<String, Object> transactionsSummary = (Map<String, Object>) accountData.get("transactionsSummary");
            
            NormalizedBalance balance = normalizeBalance(balances, "foreign-current-accounts");
            List<NormalizedTransaction> normalizedTransactions = normalizeTransactions(transactions, "foreign-current-accounts");
            NormalizedTransactionsSummary summary = normalizeTransactionsSummary(transactionsSummary, "foreign-current-accounts");
            
            // Preserve domain-specific fields
            Map<String, Object> domainSpecific = new HashMap<>();
            domainSpecific.put("account", account);
            domainSpecific.put("pagination", accountData.get("pagination"));
            
            NormalizedEntity entity = NormalizedEntity.builder()
                    .entityId((String) account.get("accountId"))
                    .entityType("ACCOUNT")
                    .nickname((String) account.get("nickname"))
                    .currency((String) account.get("currency"))
                    .status((String) account.get("status"))
                    .balance(balance)
                    .transactions(normalizedTransactions)
                    .transactionsSummary(summary)
                    .domainSpecific(domainSpecific)
                    .build();
            
            entities.add(entity);
        }
        
        return entities;
    }
    
    /**
     * Normalizes balance structure for current accounts, loans, mortgages, deposits, foreign accounts.
     */
    @SuppressWarnings("unchecked")
    private NormalizedBalance normalizeBalance(Map<String, Object> balances, String domain) {
        if (balances == null) {
            return null;
        }
        
        return NormalizedBalance.builder()
                .asOf((String) balances.get("asOf"))
                .currency(extractCurrency(balances))
                .current(extractDouble(balances, "current"))
                .available(extractDouble(balances, "available"))
                .pending(extractDouble(balances, "holds"))
                .creditLimit(extractDouble(balances, "creditLimit"))
                .availableCredit(null) // Not applicable for these domains
                .principalOutstanding(extractDouble(balances, "principalOutstanding"))
                .accruedInterest(extractDouble(balances, "accruedInterest"))
                .totalOutstanding(extractDouble(balances, "totalOutstanding"))
                .marketValue(null)
                .cashBalance(null)
                .totalValue(null)
                .build();
    }
    
    /**
     * Normalizes balance structure for credit cards.
     */
    @SuppressWarnings("unchecked")
    private NormalizedBalance normalizeCreditCardBalance(Map<String, Object> currentBalance, Map<String, Object> limits) {
        NormalizedBalance.NormalizedBalanceBuilder builder = NormalizedBalance.builder();
        
        if (currentBalance != null) {
            builder.asOf((String) currentBalance.get("asOf"))
                   .currency((String) currentBalance.get("currency"))
                   .current(extractDouble(currentBalance, "postedBalance"))
                   .pending(extractDouble(currentBalance, "pendingAmount"));
        }
        
        if (limits != null) {
            builder.creditLimit(extractDouble(limits, "creditLimit"))
                   .availableCredit(extractDouble(limits, "availableCredit"));
        }
        
        return builder.build();
    }
    
    /**
     * Normalizes balance structure for securities.
     */
    @SuppressWarnings("unchecked")
    private NormalizedBalance normalizeSecuritiesBalance(Map<String, Object> valuation) {
        if (valuation == null) {
            return null;
        }
        
        return NormalizedBalance.builder()
                .asOf((String) valuation.get("asOf"))
                .currency((String) valuation.get("baseCurrency"))
                .current(null)
                .available(null)
                .pending(null)
                .creditLimit(null)
                .availableCredit(null)
                .principalOutstanding(null)
                .accruedInterest(null)
                .totalOutstanding(null)
                .marketValue(extractDouble(valuation, "marketValueBase"))
                .cashBalance(extractDouble(valuation, "cashBalanceBase"))
                .totalValue(extractDouble(valuation, "totalValueBase"))
                .build();
    }
    
    /**
     * Normalizes transactions list.
     */
    @SuppressWarnings("unchecked")
    private List<NormalizedTransaction> normalizeTransactions(List<Map<String, Object>> transactions, String domain) {
        if (transactions == null) {
            return new ArrayList<>();
        }
        
        return transactions.stream()
                .map(tx -> normalizeTransaction(tx, domain))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * Normalizes a single transaction.
     */
    @SuppressWarnings("unchecked")
    private NormalizedTransaction normalizeTransaction(Map<String, Object> transaction, String domain) {
        if (transaction == null) {
            return null;
        }
        
        // Extract date fields based on domain
        String date = null;
        String valueDate = null;
        
        if ("credit-cards".equals(domain)) {
            date = (String) transaction.get("transactionDate");
            valueDate = (String) transaction.get("postingDate");
        } else {
            date = (String) transaction.get("bookingDate");
            valueDate = (String) transaction.get("valueDate");
        }
        
        // Extract merchant
        NormalizedMerchant merchant = normalizeMerchant((Map<String, Object>) transaction.get("merchant"));
        
        // Extract category
        NormalizedCategory category = normalizeCategory((Map<String, Object>) transaction.get("category"));
        
        // Extract counterparty
        NormalizedCounterparty counterparty = normalizeCounterparty((Map<String, Object>) transaction.get("counterparty"));
        
        // Extract references
        NormalizedTransactionReferences references = normalizeReferences((Map<String, Object>) transaction.get("references"), domain);
        
        // Extract enrichment
        NormalizedEnrichment enrichment = normalizeEnrichment((Map<String, Object>) transaction.get("enrichment"));
        
        // Extract installments (credit cards only)
        NormalizedInstallments installments = null;
        if ("credit-cards".equals(domain)) {
            installments = normalizeInstallments((Map<String, Object>) transaction.get("installments"));
        }
        
        // Extract FX rate (foreign accounts only)
        NormalizedFxRate fxRate = null;
        if ("foreign-current-accounts".equals(domain)) {
            fxRate = normalizeFxRate((Map<String, Object>) transaction.get("fxRate"));
        }
        
        return NormalizedTransaction.builder()
                .transactionId((String) transaction.get("transactionId"))
                .type((String) transaction.get("type"))
                .status((String) transaction.get("status"))
                .amount(extractDouble(transaction, "amount"))
                .currency((String) transaction.get("currency"))
                .date(date)
                .valueDate(valueDate)
                .description((String) transaction.get("description"))
                .merchant(merchant)
                .category(category)
                .counterparty(counterparty)
                .references(references)
                .enrichment(enrichment)
                .installments(installments)
                .fxRate(fxRate)
                .build();
    }
    
    /**
     * Normalizes merchant information.
     */
    @SuppressWarnings("unchecked")
    private NormalizedMerchant normalizeMerchant(Map<String, Object> merchant) {
        if (merchant == null) {
            return null;
        }
        
        Map<String, Object> location = (Map<String, Object>) merchant.get("location");
        NormalizedLocation normalizedLocation = null;
        if (location != null) {
            normalizedLocation = NormalizedLocation.builder()
                    .city((String) location.get("city"))
                    .country((String) location.get("country"))
                    .build();
        }
        
        return NormalizedMerchant.builder()
                .name((String) merchant.get("name"))
                .mcc((String) merchant.get("mcc"))
                .location(normalizedLocation)
                .build();
    }
    
    /**
     * Normalizes category information.
     */
    @SuppressWarnings("unchecked")
    private NormalizedCategory normalizeCategory(Map<String, Object> category) {
        if (category == null) {
            return null;
        }
        
        return NormalizedCategory.builder()
                .code((String) category.get("code"))
                .label((String) category.get("label"))
                .build();
    }
    
    /**
     * Normalizes counterparty information.
     */
    @SuppressWarnings("unchecked")
    private NormalizedCounterparty normalizeCounterparty(Map<String, Object> counterparty) {
        if (counterparty == null) {
            return null;
        }
        
        return NormalizedCounterparty.builder()
                .name((String) counterparty.get("name"))
                .bankName((String) counterparty.get("bankName"))
                .accountMasked((String) counterparty.get("accountMasked"))
                .build();
    }
    
    /**
     * Normalizes transaction references.
     */
    @SuppressWarnings("unchecked")
    private NormalizedTransactionReferences normalizeReferences(Map<String, Object> references, String domain) {
        if (references == null) {
            return null;
        }
        
        return NormalizedTransactionReferences.builder()
                .bankReference((String) references.get("bankReference"))
                .endToEndId((String) references.get("endToEndId"))
                .authorizationCode((String) references.get("authorizationCode"))
                .rrn((String) references.get("rrn"))
                .issuerReference((String) references.get("issuerReference"))
                .build();
    }
    
    /**
     * Normalizes enrichment data.
     */
    @SuppressWarnings("unchecked")
    private NormalizedEnrichment normalizeEnrichment(Map<String, Object> enrichment) {
        if (enrichment == null) {
            return null;
        }
        
        List<String> tags = (List<String>) enrichment.get("tags");
        
        return NormalizedEnrichment.builder()
                .normalizedDescription((String) enrichment.get("normalizedDescription"))
                .tags(tags)
                .build();
    }
    
    /**
     * Normalizes installment information.
     */
    @SuppressWarnings("unchecked")
    private NormalizedInstallments normalizeInstallments(Map<String, Object> installments) {
        if (installments == null) {
            return null;
        }
        
        return NormalizedInstallments.builder()
                .isInstallment((Boolean) installments.get("isInstallment"))
                .planType((String) installments.get("planType"))
                .totalInstallments(extractInteger(installments, "totalInstallments"))
                .currentInstallment(extractInteger(installments, "currentInstallment"))
                .installmentAmount(extractDouble(installments, "installmentAmount"))
                .build();
    }
    
    /**
     * Normalizes FX rate information.
     */
    @SuppressWarnings("unchecked")
    private NormalizedFxRate normalizeFxRate(Map<String, Object> fxRate) {
        if (fxRate == null) {
            return null;
        }
        
        return NormalizedFxRate.builder()
                .baseCurrency((String) fxRate.get("baseCurrency"))
                .quoteCurrency((String) fxRate.get("quoteCurrency"))
                .rate(extractDouble(fxRate, "rate"))
                .rateTimestamp((String) fxRate.get("rateTimestamp"))
                .rateType((String) fxRate.get("rateType"))
                .source((String) fxRate.get("source"))
                .appliedTo((String) fxRate.get("appliedTo"))
                .convertedAmountIls(extractDouble(fxRate, "convertedAmountIls"))
                .isFinal((Boolean) fxRate.get("isFinal"))
                .build();
    }
    
    /**
     * Normalizes transactions summary.
     */
    @SuppressWarnings("unchecked")
    private NormalizedTransactionsSummary normalizeTransactionsSummary(Map<String, Object> summary, String domain) {
        if (summary == null) {
            return null;
        }
        
        // Extract largest debit/credit
        NormalizedTransactionReference largestDebit = normalizeTransactionReference(
                (Map<String, Object>) summary.get("largestDebit"));
        NormalizedTransactionReference largestCredit = normalizeTransactionReference(
                (Map<String, Object>) summary.get("largestCredit"));
        
        // Extract ILS equivalent (foreign accounts only)
        NormalizedIlsEquivalent ilsEquivalent = null;
        if ("foreign-current-accounts".equals(domain)) {
            Map<String, Object> ilsEq = (Map<String, Object>) summary.get("ilsEquivalent");
            if (ilsEq != null) {
                ilsEquivalent = NormalizedIlsEquivalent.builder()
                        .totalDebitsIls(extractDouble(ilsEq, "totalDebitsIls"))
                        .totalCreditsIls(extractDouble(ilsEq, "totalCreditsIls"))
                        .fxMethod((String) ilsEq.get("fxMethod"))
                        .build();
            }
        }
        
        return NormalizedTransactionsSummary.builder()
                .fromDate((String) summary.get("fromDate"))
                .toDate((String) summary.get("toDate"))
                .transactionCount(extractInteger(summary, "transactionCount"))
                .totalDebits(extractDouble(summary, "totalDebits"))
                .totalCredits(extractDouble(summary, "totalCredits"))
                .largestDebit(largestDebit)
                .largestCredit(largestCredit)
                .lastActivityDate((String) summary.get("lastActivityDate"))
                .ilsEquivalent(ilsEquivalent)
                .build();
    }
    
    /**
     * Normalizes transaction reference (for largest debit/credit).
     */
    @SuppressWarnings("unchecked")
    private NormalizedTransactionReference normalizeTransactionReference(Map<String, Object> ref) {
        if (ref == null) {
            return null;
        }
        
        return NormalizedTransactionReference.builder()
                .amount(extractDouble(ref, "amount"))
                .currency((String) ref.get("currency"))
                .transactionId((String) ref.get("transactionId"))
                .bookingDate((String) ref.get("bookingDate"))
                .description((String) ref.get("description"))
                .build();
    }
    
    /**
     * Normalizes metadata.
     */
    @SuppressWarnings("unchecked")
    private NormalizedMetadata normalizeMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        
        return NormalizedMetadata.builder()
                .schemaVersion((String) metadata.get("schemaVersion"))
                .currencyDecimals((Map<String, Integer>) metadata.get("currencyDecimals"))
                .disclaimers((List<String>) metadata.get("disclaimers"))
                .build();
    }
    
    // Helper methods
    
    private Double extractDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }
    
    private Integer extractInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }
    
    private String extractCurrency(Map<String, Object> balances) {
        // Try common currency fields
        String currency = (String) balances.get("currency");
        if (currency == null) {
            currency = (String) balances.get("baseCurrency");
        }
        return currency;
    }
}
