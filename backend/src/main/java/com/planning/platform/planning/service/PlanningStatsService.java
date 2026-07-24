package com.planning.platform.planning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.planning.domain.BizDayPlan;
import com.planning.platform.planning.domain.BizResult;
import com.planning.platform.planning.mapper.BizDayPlanMapper;
import com.planning.platform.planning.mapper.BizResultMapper;
import com.planning.platform.planning.vo.PlanningStatsRespVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlanningStatsService {

    private final BizDayPlanMapper dayPlanMapper;
    private final BizResultMapper resultMapper;
    private final PlanningAccessService accessService;

    public PlanningStatsRespVO stats(AuthUser user) {
        LambdaQueryWrapper<BizDayPlan> pendingQuery = baseDayQuery(user)
                .eq(BizDayPlan::getStatus, "PENDING");
        Long pending = dayPlanMapper.selectCount(pendingQuery);

        Long overdue = dayPlanMapper.selectCount(baseDayQuery(user)
                .eq(BizDayPlan::getStatus, "PENDING")
                .le(BizDayPlan::getPlanDate, LocalDate.now().minusDays(3)));

        LocalDate firstDay = LocalDate.now().withDayOfMonth(1);
        LocalDate lastDay = firstDay.plusMonths(1).minusDays(1);
        LambdaQueryWrapper<BizResult> resultQuery = new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .ge(BizResult::getResultDate, firstDay)
                .le(BizResult::getResultDate, lastDay)
                .in(BizResult::getOwnerUserId, accessService.accessibleOwnerIds(user));
        Long currentMonthResults = resultMapper.selectCount(resultQuery);

        Long totalSubmitted = dayPlanMapper.selectCount(baseDayQuery(user)
                .in(BizDayPlan::getStatus, "PENDING", "APPROVED", "REJECTED"));
        Long closed = dayPlanMapper.selectCount(baseDayQuery(user)
                .in(BizDayPlan::getStatus, "APPROVED", "REJECTED"));
        String closureRate = totalSubmitted == 0 ? "--" : Math.round(closed * 100.0 / totalSubmitted) + "%";

        return new PlanningStatsRespVO(pending, overdue, currentMonthResults, closureRate);
    }

    private LambdaQueryWrapper<BizDayPlan> baseDayQuery(AuthUser user) {
        Set<Long> ownerIds = accessService.accessibleOwnerIds(user);
        return new LambdaQueryWrapper<BizDayPlan>()
                .eq(BizDayPlan::getDeleted, 0)
                .in(BizDayPlan::getOwnerUserId, ownerIds);
    }
}
