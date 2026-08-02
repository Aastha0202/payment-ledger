package com.paymentledger.command_service.repository;

import com.paymentledger.command_service.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AccountReconciliationRepository
        extends JpaRepository<Account, UUID> {

    @Query(value = """
        SELECT
            a.id as account_id,
            a.balance as cached_balance,
            COALESCE(
                    SUM(
                CASE
                    WHEN je.entry_type = 'CREDIT' 
                        THEN je.amount
                    WHEN je.entry_type = 'DEBIT'  
                        THEN -je.amount
                    WHEN je.entry_type = 'COMPENSATION' 
                        THEN je.amount
                    ELSE 0
                END
            ), 0) as calculated_balance
        FROM accounts a
        LEFT JOIN journal_entries je 
            ON je.account_id = a.id
        GROUP BY a.id, a.balance
        HAVING a.balance != COALESCE(SUM(
            CASE
                WHEN je.entry_type = 'CREDIT' 
                    THEN je.amount
                WHEN je.entry_type = 'DEBIT'  
                    THEN -je.amount
                WHEN je.entry_type = 'COMPENSATION' 
                    THEN je.amount
                ELSE 0
            END
        ), 0)
        """, nativeQuery = true)
    List<Object[]> findAccountsWithBalanceMismatch();
}