package com.planning.platform.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.planning.platform.system.domain.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT id FROM sys_user WHERE id = #{id} FOR UPDATE")
    Long selectIdForUpdate(@Param("id") Long id);
}
