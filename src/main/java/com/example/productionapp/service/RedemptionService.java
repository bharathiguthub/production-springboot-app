package com.example.productionapp.service;

import com.example.productionapp.dto.RedemptionResponse;
import com.example.productionapp.entity.Redemption;
import com.example.productionapp.exception.RedemptionNotFoundException;
import com.example.productionapp.mapper.RedemptionMapper;
import com.example.productionapp.repository.RedemptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RedemptionService {

    private final RedemptionRepository redemptionRepository;
    private final RedemptionMapper redemptionMapper;

    public RedemptionService(RedemptionRepository redemptionRepository, RedemptionMapper redemptionMapper) {
        this.redemptionRepository = redemptionRepository;
        this.redemptionMapper = redemptionMapper;
    }

    @Transactional(readOnly = true)
    public RedemptionResponse getRedemptionByRedemptionId(String redemptionId) {
        Redemption redemption = redemptionRepository.findByRedemptionId(redemptionId)
                .orElseThrow(() -> new RedemptionNotFoundException("Redemption not found with redemptionId: " + redemptionId));
        return redemptionMapper.toResponse(redemption);
    }
}
