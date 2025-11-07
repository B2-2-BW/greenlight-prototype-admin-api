package com.winten.greenlight.admin.domain.action;

import com.winten.greenlight.admin.db.repository.redis.RedisWriter;
import com.winten.greenlight.admin.support.util.RedisKeyBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActionCacheManager {
    private final RedisKeyBuilder keyBuilder;
    private final RedisWriter redisWriter;
    private final ActionConverter actionConverter;

    /**
     * Action이 변경될 때 redis에 캐시 저장
     */
    public void updateActionCache(Action action) {
        String actionKey = keyBuilder.action(action.getId());
        redisWriter.putAll(actionKey, actionConverter.toEntity(action));

        // url을 기준으로 action을 조회할 수 있는 기능을 위해 redis에 key로 저장
        if (action.getActionType() == ActionType.DIRECT) {
            String urlMappingKey = keyBuilder.urlCachingKey(action.getActionUrl());
            redisWriter.put(urlMappingKey, String.valueOf(action.getId()));
        }

        // landingId를 기준으로 action을 조회할 수 있는 기능을 위해 redis에 key로 저장
//        if (action.getActionType() == ActionType.LANDING) {
        String landingMappingKey = keyBuilder.landingCacheKey(action.getLandingId());
        redisWriter.put(landingMappingKey, String.valueOf(action.getId()));
//        }
    }

    /**
     * Action이 삭제될 때 redis에서 캐시 삭제
     */
    public void deleteActionCache(Action action) {
        String actionKey = keyBuilder.action(action.getId());
        redisWriter.delete(actionKey);

        String urlMappingKey = keyBuilder.urlCachingKey(action.getActionUrl());
        redisWriter.delete(urlMappingKey);

        String landingMappingKey = keyBuilder.landingCacheKey(action.getLandingId());
        redisWriter.delete(landingMappingKey);
    }
}