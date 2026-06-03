package com.drm.auth.controller;

import com.drm.auth.entity.Transaction;
import com.drm.auth.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TransactionController {

    private final TransactionRepository transactionRepo;

    @GetMapping
    public ResponseEntity<List<Transaction>> getTransactions(@RequestParam(value = "username", required = false) String username) {
        if (username != null && !username.trim().isEmpty()) {
            return ResponseEntity.ok(transactionRepo.findBySenderUsernameOrReceiverUsernameOrderByTimestampDesc(username, username));
        }
        return ResponseEntity.ok(transactionRepo.findAllByOrderByTimestampDesc());
    }
}
