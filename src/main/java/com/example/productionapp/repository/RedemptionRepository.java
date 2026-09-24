package com.example.productionapp.repository;

import com.example.productionapp.entity.Redemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RedemptionRepository extends JpaRepository<Redemption, Long> {

    Optional<Redemption> findByRedemptionId(String redemptionId);
}
