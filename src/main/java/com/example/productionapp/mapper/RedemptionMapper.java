package com.example.productionapp.mapper;

import com.example.productionapp.dto.RedemptionResponse;
import com.example.productionapp.entity.Redemption;
import org.springframework.stereotype.Component;

@Component
public class RedemptionMapper {

    public RedemptionResponse toResponse(Redemption redemption) {
        return new RedemptionResponse(
                redemption.getId(),
                redemption.getRedemptionId(),
                redemption.getCustomerId(),
                redemption.getVendor(),
                redemption.getPoints(),
                redemption.getAmount(),
                redemption.getCurrency(),
                redemption.getStatus(),
                redemption.getErrorCode(),
                redemption.getCreatedAt(),
                redemption.getUpdatedAt()
        );
    }
}
