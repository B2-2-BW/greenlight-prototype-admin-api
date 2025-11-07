package com.winten.greenlight.admin.domain.actiongroup;

import com.winten.greenlight.admin.db.repository.mapper.actiongroup.ActionGroupMapper;
import com.winten.greenlight.admin.domain.action.Action;
import com.winten.greenlight.admin.domain.action.ActionService;
import com.winten.greenlight.admin.domain.user.CurrentUser;
import com.winten.greenlight.admin.domain.user.UserService;
import com.winten.greenlight.admin.support.error.CoreException;
import com.winten.greenlight.admin.support.error.ErrorType;
import com.winten.greenlight.admin.support.util.RedisKeyBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActionGroupService {
    private final RedisTemplate<String, String> stringRedisTemplate;
    private final RedisKeyBuilder keyBuilder;
    private final ActionGroupMapper actionGroupMapper;
    private final ActionService actionService;
    private final UserService userService;
    private final CachedActionGroupService cachedActionGroupService;
    private final ActionGroupCacheManager actionGroupCacheManager;

    public List<ActionGroup> getAllActionGroupByOwnerId(ActionGroup actionGroup) {
        return actionGroupMapper.findAll(actionGroup);
    }

    public ActionGroup getActionGroupById(Long id, CurrentUser currentUser) {
        ActionGroup actionGroup = ActionGroup.builder()
                                        .id(id)
                                        .ownerId(currentUser.getUserId())
                                        .build();
        return actionGroupMapper.findOneById(actionGroup)
                .orElseThrow(() -> CoreException.of(ErrorType.ACTION_GROUP_NOT_FOUND, "액션 그룹을 찾을 수 없습니다. ID: " + id));
    }

    public ActionGroup getActionGroupByIdWithAction(Long id, CurrentUser currentUser) {
        ActionGroup actionGroup = getActionGroupById(id, currentUser);
        List<Action> actions = actionService.getActionsByGroup(id, currentUser);
        actionGroup.setActions(actions);
        return actionGroup;
    }


    @Transactional
    public ActionGroup createActionGroup(ActionGroup actionGroup, CurrentUser currentUser) {
        actionGroup.setOwnerId(currentUser.getUserId());
        ActionGroup result = actionGroupMapper.save(actionGroup);

        // Redis put
        actionGroupCacheManager.updateActionGroupMetaCache(result);

        return result;
    }

    @Transactional
    public ActionGroup updateActionGroup(ActionGroup actionGroup, CurrentUser currentUser) {
        // TODO currentUser가 ADMIN인 경우 ownerId를 맘대로 변경 가능. 현재는 currentUser의 userId로 강제 입력중
        ActionGroup currentActionGroup = getActionGroupById(actionGroup.getId(), currentUser); // action group 존재여부 확인
        actionGroup.setOwnerId(currentUser.getUserId());

        ActionGroup result = actionGroupMapper.updateById(actionGroup);

        // Redis put
        actionGroupCacheManager.updateActionGroupMetaCache(result);

        // 활성화 상태 변경 시 action 캐시 업데이트
        if (currentActionGroup.getEnabled() != result.getEnabled()) {
            actionService.reloadActionCache(currentUser);
        }
        return result;
    }

    @Transactional
    public ActionGroup deleteActionGroup(Long id, CurrentUser currentUser) {
        ActionGroup actionGroup = getActionGroupById(id, currentUser); // action group 존재여부 확인

        List<Action> actions = actionService.getActionsByGroup(id, currentUser);

        if (!actions.isEmpty()) {
            throw CoreException.of(ErrorType.NONEMPTY_ACTION_GROUP, "액션 그룹 내에 액션이 존재하여 삭제할 수 없습니다. 액션을 다른 그룹으로 이동하거나 삭제해 주세요.");
        }

        actionGroupMapper.deleteById(actionGroup);

        // Redis delete
        actionGroupCacheManager.deleteActionGroupMetaCache(actionGroup);

//        coreClient.invalidateActionGroupCacheById(actionGroup.getId());

        return ActionGroup.builder()
                .id(id)
                .build();
    }

    public List<ActionGroup> getActionGroupByKey(String greenlightApiKey) {
        var user = userService.getUserAccountIdByKey(greenlightApiKey);
        var actionGroup = ActionGroup.builder()
                .ownerId(user.getUserId())
                .build();
        return actionGroupMapper.findAllEnabledWithActions(actionGroup);
    }

    // action_group:{actionGroup}:queue:WAITING, action_group:{actionGroup}:session의 size 조회
    public List<ActionGroupQueue> getActionGroupQueueStatus() {
        List<ActionGroup> allActionGroups = cachedActionGroupService.getAllActionGroup();

        List<ActionGroup> enabledActionGroups = allActionGroups.stream().filter(ActionGroup::getEnabled).toList();

        List<ActionGroupQueue> result = new ArrayList<>();
        if (enabledActionGroups.isEmpty()) { // enabledActionGroups != null 보장됨
            return result;
        }

        // RTT 절감을 위해 redis 명령어 파이프라이닝
        List<Object> waitingQueueSizes = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (ActionGroup actionGroup : enabledActionGroups) {
                String key = keyBuilder.actionGroupWaitingQueue(actionGroup.getId());
                connection.zSetCommands().zCard(key.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });

        List<Object> maxTrafficPerSecondList = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (ActionGroup actionGroup : enabledActionGroups) {
                String key = keyBuilder.actionGroupMeta(actionGroup.getId());
                connection.hashCommands().hGet(key.getBytes(StandardCharsets.UTF_8), "maxTrafficPerSecond".getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });

        for (int i = 0; i < enabledActionGroups.size(); i++) {
            Long id = enabledActionGroups.get(i).getId();
            int waitingSize = 0;
            int estimatedWaitTime = 0;
//            int activeUserCount = 0;
            try {
                waitingSize = Integer.parseInt(waitingQueueSizes.get(i).toString());
//                activeUserCount = Integer.parseInt(activeUserCounts.get(i).toString());
                int maxTrafficPerSecond = Integer.parseInt(maxTrafficPerSecondList.get(i).toString());
                estimatedWaitTime = maxTrafficPerSecond > 0
                        ? Math.round((float) waitingSize / maxTrafficPerSecond)
                        : -1; // -1은 진입불가 
            } catch (Exception e) {
                log.error("[getAllWaitingQueueSize] parsing waiting queue size failed");
            }
            var queue = ActionGroupQueue.builder()
                    .actionGroupId(id)
                    .waitingSize(waitingSize)
                    .estimatedWaitTime(estimatedWaitTime)
//                    .activeUserCount(activeUserCount)
                    .build();
            result.add(queue);
        }

        return result;
    }

    public Long getSessionCount() {
        var key = keyBuilder.session();
        return stringRedisTemplate.opsForZSet().size(key);

    }

    public void reloadActionGroupCache(CurrentUser currentUser) {
        ActionGroup param = ActionGroup.builder()
                        .ownerId(currentUser.getUserId())
                        .build();
        List<ActionGroup> actionGroupList = getAllActionGroupByOwnerId(param);
        for (ActionGroup actionGroup : actionGroupList) {
            actionGroupCacheManager.deleteActionGroupMetaCache(actionGroup);
            if (actionGroup.getEnabled()) {
                actionGroupCacheManager.updateActionGroupMetaCache(actionGroup);
            }
        }
        actionService.reloadActionCache(currentUser);
    }
}