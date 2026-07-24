package com.planning.platform.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.planning.platform.system.domain.SysPermission;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    @Select("""
            SELECT p.code
            FROM sys_permission p
            INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
            INNER JOIN sys_role r ON r.id = rp.role_id
            INNER JOIN sys_user_role ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = #{userId}
              AND r.status = 1
              AND r.deleted = 0
              AND p.status = 1
              AND p.deleted = 0
            GROUP BY p.id, p.code, p.sort_no
            ORDER BY p.sort_no, p.id
            """)
    List<String> selectPermissionCodesByUserId(Long userId);
}
