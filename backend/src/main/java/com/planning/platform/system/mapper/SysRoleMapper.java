package com.planning.platform.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.planning.platform.system.domain.SysRole;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("""
            SELECT r.code
            FROM sys_role r
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
              AND r.status = 1
              AND r.deleted = 0
            ORDER BY r.id
            """)
    List<String> selectRoleCodesByUserId(Long userId);
}
