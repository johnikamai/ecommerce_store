package com.ecommerce.order.service;
import com.ecommerce.order.model.CheckoutAttempt;
import com.ecommerce.order.repo.CheckoutAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HexFormat;
import java.security.MessageDigest;
@Service
public class OperationJournal {
    private final CheckoutAttemptRepository attempts;private final ObjectMapper json;private final TransactionTemplate tx;
    public OperationJournal(CheckoutAttemptRepository attempts,ObjectMapper json,PlatformTransactionManager manager){this.attempts=attempts;this.json=json;tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);}
    public void begin(String id,String kind,Object payload){
        try{String body=json.writeValueAsString(payload);String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            tx.executeWithoutResult(s->{var known=attempts.lock(id);if(known.isPresent()){if(!known.get().getRequestHash().equals(hash))throw new IllegalArgumentException("Operation inputs differ from the accepted request");return;}var row=new CheckoutAttempt();row.setId(id);row.setKind(kind);row.setRequestHash(hash);row.setSnapshot(body);attempts.saveAndFlush(row);});
        }catch(java.io.IOException|java.security.NoSuchAlgorithmException error){throw new IllegalArgumentException("Invalid operation payload",error);}
    }
    public void complete(String id){tx.executeWithoutResult(s->{var found=attempts.lock(id);if(found.isEmpty())return;var row=found.get();row.setState("COMPLETE");row.setError(null);attempts.save(row);});}
    public void afterCommit(String id){if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCommit(){complete(id);}});else complete(id);}
}
