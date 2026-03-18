package com.folo.trade;

import com.folo.common.enums.TradeVisibility;
import com.folo.follow.SocialRelationService;
import org.springframework.stereotype.Service;

@Service
public class TradeAccessService {

    private final SocialRelationService socialRelationService;

    public TradeAccessService(SocialRelationService socialRelationService) {
        this.socialRelationService = socialRelationService;
    }

    public boolean canView(Long viewerUserId, Trade trade) {
        if (trade.isDeleted()) {
            return false;
        }
        if (trade.getUser().getId().equals(viewerUserId)) {
            return true;
        }
        return switch (trade.getVisibility()) {
            case PUBLIC -> true;
            case FRIENDS_ONLY -> socialRelationService.isMutualFollow(viewerUserId, trade.getUser().getId());
            case PRIVATE -> false;
        };
    }

    public boolean canInteract(Long viewerUserId, Trade trade) {
        return canView(viewerUserId, trade);
    }

    public boolean visibleOnFeed(Long viewerUserId, Trade trade) {
        if (trade.getVisibility() == TradeVisibility.PRIVATE) {
            return false;
        }
        return canView(viewerUserId, trade);
    }
}
