package com.planning.platform.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.planning.platform.system.domain.SysWorkdayRule;
import com.planning.platform.system.mapper.SysWorkdayRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkdayCalendarService {

    private final SysWorkdayRuleMapper workdayRuleMapper;

    public CalendarDay resolve(LocalDate date) {
        SysWorkdayRule rule = workdayRuleMapper.selectOne(new LambdaQueryWrapper<SysWorkdayRule>()
                .eq(SysWorkdayRule::getRuleDate, date)
                .eq(SysWorkdayRule::getStatus, 1)
                .eq(SysWorkdayRule::getDeleted, 0)
                .orderByDesc(SysWorkdayRule::getVersionNo)
                .orderByDesc(SysWorkdayRule::getId)
                .last("LIMIT 1"));
        return rule == null ? defaultRule(date) : fromRule(rule);
    }

    public List<CalendarDay> range(LocalDate start, LocalDate end) {
        List<SysWorkdayRule> rules = workdayRuleMapper.selectList(new LambdaQueryWrapper<SysWorkdayRule>()
                .between(SysWorkdayRule::getRuleDate, start, end)
                .eq(SysWorkdayRule::getStatus, 1)
                .eq(SysWorkdayRule::getDeleted, 0)
                .orderByAsc(SysWorkdayRule::getRuleDate)
                .orderByDesc(SysWorkdayRule::getVersionNo)
                .orderByDesc(SysWorkdayRule::getId));
        Map<LocalDate, SysWorkdayRule> latest = new LinkedHashMap<>();
        rules.stream()
                .sorted(Comparator.comparing(SysWorkdayRule::getRuleDate)
                        .thenComparing(SysWorkdayRule::getVersionNo, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SysWorkdayRule::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(rule -> latest.putIfAbsent(rule.getRuleDate(), rule));

        List<CalendarDay> result = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            SysWorkdayRule rule = latest.get(date);
            result.add(rule == null ? defaultRule(date) : fromRule(rule));
        }
        return result;
    }

    private CalendarDay fromRule(SysWorkdayRule rule) {
        return new CalendarDay(rule.getRuleDate(), rule.getRuleType(), Boolean.TRUE.equals(rule.getForceReport()),
                rule.getDescription(), rule.getId(), rule.getVersionNo(), true);
    }

    private CalendarDay defaultRule(LocalDate date) {
        boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
        return new CalendarDay(date, weekend ? "WEEKEND" : "WORKDAY", !weekend,
                weekend ? "默认周末，非强制填报" : "默认工作日", null, null, false);
    }

    public record CalendarDay(LocalDate date, String ruleType, boolean forceReport, String description,
                              Long ruleId, Integer versionNo, boolean explicit) {
    }
}
