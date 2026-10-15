package com.tzyl.service;


import com.tzyl.base.ResponseResult;
import com.tzyl.dto.ApplicationsDto;
import com.tzyl.vo.ApplicationsVo;

/**
* <p>
* applications Service 接口
* </p>
*
* @author husiyu
*/
public interface ApplicationsService {

    /**
     * 分页查询我的申请
     * @param applicationsDto
     * @return
     */
    ResponseResult<ApplicationsVo> selectByPage(ApplicationsDto applicationsDto);

}