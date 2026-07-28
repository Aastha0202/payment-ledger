package com.paymentledger.command_service.repository;

import com.paymentledger.command_service.constants.TransferSagaStatus;
import com.paymentledger.command_service.entity.TransferSaga;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransferSagaRepository extends JpaRepository<TransferSaga, UUID> {

    List<TransferSaga> findByStatusIn(List<TransferSagaStatus> statuses);
}
