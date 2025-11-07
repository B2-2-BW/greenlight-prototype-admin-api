package com.winten.greenlight.admin.domain.action;

import com.winten.greenlight.admin.db.repository.mapper.action.ActionMapper;
import com.winten.greenlight.admin.domain.actionrule.ActionRuleService;
import com.winten.greenlight.admin.domain.user.CurrentUser;
import com.winten.greenlight.admin.support.error.CoreException;
import com.winten.greenlight.admin.support.error.ErrorType;
import io.hypersistence.tsid.TSID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActionService {
    private final ActionMapper actionMapper;
    private final ActionRuleService actionRuleService;
    private final ActionCacheManager actionCacheManager;

    // TODO Action Rule 추가하기
    public List<Action> getAllActionsByOwnerId(String ownerId) {
        return actionMapper.findAll(ownerId);
    }

    public List<Action> getAllEnabledActionsByOwnerId(String ownerId) {
        return actionMapper.findAllEnabled(ownerId);
    }

    public Action getActionById(Long id, CurrentUser currentUser) {
        Action action = Action.builder()
                .id(id)
                .ownerId(currentUser.getUserId())
                .build();
        return actionMapper.findOneById(action)
                .orElseThrow(() -> CoreException.of(ErrorType.ACTION_NOT_FOUND, "액션을 찾을 수 없습니다. ID: " + id));
    }

    public Action getActionByIdWithRules(Long id, CurrentUser currentUser) {
        Action action = getActionById(id, currentUser);
        List<ActionRule> actionRules = actionRuleService.findAllActionRuleByActionId(id);
        action.setActionRules(actionRules);
        return action;
    }

    public List<Action> getActionsByGroup(Long actionGroupId, CurrentUser currentUser) {
        Action param = Action.builder()
                .actionGroupId(actionGroupId)
                .ownerId(currentUser.getUserId())
                .build();
        param.setOwnerId(currentUser.getUserId());
        List<Action> actions = actionMapper.findAllByGroupId(param);
        for (Action action : actions) {
            List<ActionRule> actionRules = actionRuleService.findAllActionRuleByActionId(action.getId());
            action.setActionRules(actionRules);
        }
        return actions;
    }

    @Transactional
    public Action createActionInGroup(
            Long actionGroupId,
            Action actionParam,
            CurrentUser currentUser
    ) {
        // DB Insert
        actionParam.setActionGroupId(actionGroupId);
        actionParam.setOwnerId(currentUser.getUserId());
        actionParam.setLandingId(TSID.fast().toString()); // actionType과 관계없이 고유한 LandingId 부여

        validateActionType(actionParam); // actionType 검증

        // Action 저장
        Action actionResult = actionMapper.save(actionParam);

        // Action ID 세팅
        for (ActionRule actionRule : actionParam.getActionRules()) {
            actionRule.setActionId(actionResult.getId());
        }
        // Action Rule 저장
        actionRuleService.saveAll(actionParam.getActionRules(), currentUser);
        List<ActionRule> actionRuleResult = actionRuleService.findAllActionRuleByActionId(actionResult.getId());
        actionResult.setActionRules(actionRuleResult);

        actionCacheManager.updateActionCache(actionResult);

        return actionResult;
    }

    public Action updateActionById(
            Action actionParam,
            CurrentUser currentUser
    ) {
        var currentAction = getActionById(actionParam.getId(), currentUser); // 존재여부 확인, 없으면 exception
        actionParam.setOwnerId(currentUser.getUserId());

        validateActionType(actionParam); // actionType 검증
        
        // DB Update
        Action actionResult = actionMapper.updateById(actionParam);
        
        // TODO AWS처럼 action rule 개별 업데이트 및 삭제가 가능해야할수도?
        //  현재는 전체 삭제 후 다시 insert 중임
        // Action Rule 삭제 후 저장
        actionRuleService.deleteAllByActionId(actionResult.getId());

        for (ActionRule actionRule : actionParam.getActionRules()) {
            actionRule.setActionId(actionResult.getId());
        }
        actionRuleService.saveAll(actionParam.getActionRules(), currentUser);

        List<ActionRule> actionRuleResult = actionRuleService.findAllActionRuleByActionId(actionResult.getId());
        actionResult.setActionRules(actionRuleResult);

        actionCacheManager.updateActionCache(actionResult);

        return actionResult;
    }

    public Action deleteActionById(Long actionId, CurrentUser currentUser) {
        Action action = getActionById(actionId, currentUser); // 존재여부 확인, 없으면 exception

        // DB Delete
        actionMapper.deleteById(action);

        actionRuleService.deleteAllByActionId(actionId);

        actionCacheManager.deleteActionCache(action);

        return Action.builder()
                .id(actionId)
                .build();
    }

    private void validateActionType(Action action) {
        if (action.getActionType() == ActionType.LANDING) {
            if (action.getLandingDestinationUrl() == null || action.getLandingDestinationUrl().isEmpty()) {
                throw CoreException.of(ErrorType.INVALID_DATA, "액션유형이 LANDING인 경우 랜딩 목적지(landingDestinationUrl)는 필수로 입력되어야 합니다.");
            }
            if (action.getLandingStartAt() == null) {
                throw CoreException.of(ErrorType.INVALID_DATA, "액션유형이 LANDING인 경우 랜딩 시작시간(landingStartAt)은 필수로 입력되어야 합니다.");
            }
            if (action.getLandingEndAt() == null) {
                throw CoreException.of(ErrorType.INVALID_DATA, "액션유형이 LANDING인 경우 랜딩 종료시간(landingEndAt)은 필수로 입력되어야 합니다.");
            }
        }
    }

    public void reloadActionCache(CurrentUser currentUser) {
        // 기존 액션 전체 삭제
        List<Action> allActions = getAllActionsByOwnerId(currentUser.getUserId());
        for (Action action : allActions) {
            actionCacheManager.deleteActionCache(action);
        }

        List<Action> enabledActions = getAllEnabledActionsByOwnerId(currentUser.getUserId());
        for (Action action : enabledActions) {
            List<ActionRule> actionRuleResult = actionRuleService.findAllActionRuleByActionId(action.getId());
            action.setActionRules(actionRuleResult);

            actionCacheManager.updateActionCache(action);
        }
    }
}