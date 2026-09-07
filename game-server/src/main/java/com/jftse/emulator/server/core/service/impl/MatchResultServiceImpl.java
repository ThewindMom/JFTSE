package com.jftse.emulator.server.core.service.impl;

import com.jftse.emulator.server.core.service.MatchResultService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.UUID;

@Service
public class MatchResultServiceImpl implements MatchResultService {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public boolean executeOnce(String resultId, Runnable completion) {
        if (resultId == null || completion == null) {
            throw new IllegalArgumentException("Result id and completion are required");
        }
        if (resultId.length() > 36) {
            throw new IllegalArgumentException("Result id is too long");
        }
        String claimToken = UUID.randomUUID().toString();
        entityManager.createNativeQuery("""
                        INSERT INTO MatchResult (resultId, claimToken, created)
                        VALUES (:resultId, :claimToken, NOW(6))
                        ON DUPLICATE KEY UPDATE resultId = VALUES(resultId)
                        """)
                .setParameter("resultId", resultId)
                .setParameter("claimToken", claimToken)
                .executeUpdate();
        String storedClaimToken = (String) entityManager.createNativeQuery("""
                        SELECT claimToken
                        FROM MatchResult
                        WHERE resultId = :resultId
                        """)
                .setParameter("resultId", resultId)
                .getSingleResult();
        if (!claimToken.equals(storedClaimToken)) {
            return false;
        }
        completion.run();
        return true;
    }
}
