package com.paymentledger.command_service.repository;

import com.paymentledger.command_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository  extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);
}
