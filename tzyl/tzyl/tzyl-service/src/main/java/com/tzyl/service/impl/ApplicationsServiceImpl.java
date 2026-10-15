package com.tzyl.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.tzyl.base.PageResponse;
import com.tzyl.base.ResponseResult;
import com.tzyl.dto.ApplicationsDto;
import com.tzyl.dto.PendingTasksDto;
import com.tzyl.entity.PendingTasks;
import com.tzyl.service.ApplicationsService;
import com.tzyl.vo.ApplicationsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author husiyu
 */
@Service
public class ApplicationsServiceImpl implements ApplicationsService {

    @Autowired
    private ActFlowCommServiceImpl actFlowCommService;

    /**
     * 分页查询我的申请
     * @param applicationsDto
     * @return
     */
    @Override
    public ResponseResult<ApplicationsVo> selectByPage(ApplicationsDto applicationsDto) {
        PendingTasksDto pendingTasksDto = BeanUtil.toBean(applicationsDto, PendingTasksDto.class);
        PageResponse<PendingTasks> pendingTasksPageResponse = actFlowCommService.myTaskInfoList(pendingTasksDto);
        return ResponseResult.success(pendingTasksPageResponse);
    }

}
