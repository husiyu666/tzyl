package com.tzyl.controller;

import com.tzyl.base.PageResponse;
import com.tzyl.base.ResponseResult;
import com.tzyl.dto.PendingTasksDto;
import com.tzyl.entity.PendingTasks;
import com.tzyl.service.impl.ActFlowCommServiceImpl;
import com.tzyl.utils.UserThreadLocal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author husiyu
 */
@RestController
@RequestMapping("/pending_tasks")
@Api(tags = "待办")
public class PendingTasksController extends BaseController {

    @Autowired
    private ActFlowCommServiceImpl actFlowCommService;

    @PostMapping("/selectByPage")
    @ApiOperation(value = "查询待办", notes = "传入退住对象")
    public ResponseResult<PendingTasks> selectByPage(@RequestBody PendingTasksDto pendingTasksDto){
        //只查询有当前登录人的任务
        Long userId = UserThreadLocal.getMgtUserId();
        if(pendingTasksDto.getReqType() == 0){
            pendingTasksDto.setAssigneeId(userId);
        }else {
            pendingTasksDto.setApplicatId(userId);
        }

        PageResponse<PendingTasks> pendingTasksPageResponse = actFlowCommService.myTaskInfoList(pendingTasksDto);
        return success(pendingTasksPageResponse);
    }
}
