package com.paymentledger.command_service.saga;

import com.paymentledger.command_service.constants.TransferSagaStatus;
import com.paymentledger.command_service.entity.TransferSaga;
import com.paymentledger.command_service.exception.IllegalStateTransitionException;
import com.paymentledger.command_service.repository.TransferSagaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransferSagaStateMachine {

    private final TransferSagaRepository sagaRepository;

    // Define valid transitions as a Map
    private static final Map<TransferSagaStatus,
            Set<TransferSagaStatus>> VALID_TRANSITIONS = Map.of(
            TransferSagaStatus.INITIATED, Set.of(
                    TransferSagaStatus.DEBIT_PENDING,
                    TransferSagaStatus.FAILED),
            TransferSagaStatus.DEBIT_PENDING, Set.of(
                    TransferSagaStatus.DEBIT_DONE,
                    TransferSagaStatus.COMPENSATING),
            TransferSagaStatus.DEBIT_DONE, Set.of(
                    TransferSagaStatus.CREDIT_PENDING),
            TransferSagaStatus.CREDIT_PENDING, Set.of(
                    TransferSagaStatus.COMPLETED,
                    TransferSagaStatus.COMPENSATING),
            TransferSagaStatus.COMPENSATING, Set.of(
                    TransferSagaStatus.FAILED),
            TransferSagaStatus.COMPLETED, Set.of(),
            TransferSagaStatus.FAILED, Set.of()
    );

    public TransferSaga transition(
            TransferSaga saga,
            TransferSagaStatus targetStatus) {
        // your implementation
        // validate the transition is legal
        validateTransition(saga.getStatus(), targetStatus);

        TransferSagaStatus previousStatus = saga.getStatus();
        // set the new status
        saga.setStatus(targetStatus);

        //if the new status is COMPLETED or FAILED, set the completedAt timestamp
        if (targetStatus == TransferSagaStatus.COMPLETED || targetStatus == TransferSagaStatus.FAILED) {
            saga.setCompletedAt(LocalDateTime.now());
        }

        // save and return
        TransferSaga saved =  sagaRepository.save(saga);
        log.info("Saga {} transitioned: {} → {}",
                saga.getId(), previousStatus, targetStatus);
        return saved;
    }

    private void validateTransition(
            TransferSagaStatus from,
            TransferSagaStatus to) {
        if (!VALID_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateTransitionException(from, to);
        }
    }
}
