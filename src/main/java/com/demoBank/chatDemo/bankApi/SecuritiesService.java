package com.demoBank.chatDemo.bankApi;

import com.demoBank.chatDemo.repository.GenericMongoRepository;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class SecuritiesService {
    private final GenericMongoRepository repository;

    public SecuritiesService(GenericMongoRepository repository) {
        this.repository = repository;
    }

    public Document getSecuritiesData(String customerID, Boolean includePositions) throws IOException {
        Document res = repository.findById("securities", customerID);
        
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

        // Default includePositions to true if null
        boolean includePos = (includePositions != null) ? includePositions : true;
        
        for (Document accountDoc : accounts) {
            if (!includePos) {
                // Remove positions if not included (but keep positionsSummary)
                accountDoc.remove("positions");
            }
        }

        return res;
    }
}
