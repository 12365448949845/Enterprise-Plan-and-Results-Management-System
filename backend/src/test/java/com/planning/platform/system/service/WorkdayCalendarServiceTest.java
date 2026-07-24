package com.planning.platform.system.service;

import com.planning.platform.system.domain.SysWorkdayRule;
import com.planning.platform.system.mapper.SysWorkdayRuleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkdayCalendarServiceTest {

    @Mock
    private SysWorkdayRuleMapper workdayRuleMapper;

    @Test
    void weekendDefaultsToNonRequiredWhenNoExplicitRuleExists() {
        when(workdayRuleMapper.selectOne(any())).thenReturn(null);
        WorkdayCalendarService service = new WorkdayCalendarService(workdayRuleMapper);

        var result = service.resolve(LocalDate.of(2026, 7, 18));

        assertThat(result.ruleType()).isEqualTo("WEEKEND");
        assertThat(result.forceReport()).isFalse();
        assertThat(result.explicit()).isFalse();
    }

    @Test
    void explicitRuleOverridesDefaultCalendar() {
        SysWorkdayRule rule = new SysWorkdayRule();
        rule.setId(9L);
        rule.setRuleDate(LocalDate.of(2026, 7, 18));
        rule.setRuleType("SPECIAL_SHIFT");
        rule.setForceReport(true);
        rule.setDescription("周末值班");
        rule.setVersionNo(2);
        when(workdayRuleMapper.selectOne(any())).thenReturn(rule);
        WorkdayCalendarService service = new WorkdayCalendarService(workdayRuleMapper);

        var result = service.resolve(rule.getRuleDate());

        assertThat(result.ruleType()).isEqualTo("SPECIAL_SHIFT");
        assertThat(result.forceReport()).isTrue();
        assertThat(result.explicit()).isTrue();
        assertThat(result.versionNo()).isEqualTo(2);
    }
}
