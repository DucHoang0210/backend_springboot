package com.drm.auth.repository;

import com.drm.auth.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Integer> {
    List<Transaction> findAllByOrderByTimestampDesc();
    List<Transaction> findBySenderUsernameOrReceiverUsernameOrderByTimestampDesc(String senderUsername, String receiverUsername);
}
