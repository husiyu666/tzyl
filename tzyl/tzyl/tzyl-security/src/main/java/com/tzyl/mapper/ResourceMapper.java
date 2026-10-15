package com.tzyl.mapper;

import com.tzyl.dto.ResourceDto;
import com.tzyl.entity.Resource;
import com.tzyl.vo.ResourceVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ResourceMapper {

    int insert(Resource record);

    int insertSelective(Resource record);

    int updateByPrimaryKeySelective(Resource record);

    List<Resource> selectList(@Param("resourceDto") ResourceDto resourceDto);

    /**
     * 查询角色对应的菜单资源（已去重），用于构建动态菜单树
     */
    List<ResourceVo> findResourceVoListInRoleId(@Param("roleIds") List<Long> roleIds);

    /**
     * 查询角色对应的菜单资源及每个资源的授权角色（不去重），
     * 供角色编辑页判断勾选状态，不能与去重版本混用
     */
    List<ResourceVo> findResourceWithRoleVoListInRoleId(@Param("roleIds") List<Long> roleIds);

    List<ResourceVo> findButtonVoListInRoleId(@Param("roleIds") List<Long> roleIds);

    List<ResourceVo> findResourceVoListByUserId(@Param("userId") Long userId);

    /**
     * 是否存在菜单子节点
     *
     * @param menuId 菜单ID
     * @return 结果
     */
    public int hasChildByMenuId(@Param("menuId") String menuId);

    /**
     * 删除菜单管理信息
     *
     * @param menuId 菜单ID
     * @return 结果
     */
    public int deleteMenuById(@Param("menuId") String menuId);
}
