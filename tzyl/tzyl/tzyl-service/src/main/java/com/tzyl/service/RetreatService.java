package com.tzyl.service;

import com.tzyl.base.ResponseResult;
import com.tzyl.dto.RetreatDto;
import com.tzyl.dto.RetreatReqDto;
import com.tzyl.entity.Retreat;
import com.tzyl.vo.retreat.TasVo;

/**
 * @author husiyu
 */
public interface RetreatService {
    /**
     * 退住申请
     * @param retreatDto
     */
    public ResponseResult createRetreat(Retreat retreatDto);

    /**
     * 查询退住信息
     * @param      * @param status
     * @return
     */
    public ResponseResult<TasVo> getRetreat(String retreatCode, String assigneeId, Integer flowStatus, String taskId);

    /**
     * 提交退住申请
     *  -护理组长审核
     *  -法务人员提交
     *  -结算员提交
     *  -结算员组长审核
     *  -副院长审核
     *  -结算员调整账单提交
     * @param retreatDto
     * @return
     */
    ResponseResult submitRetreat(RetreatDto retreatDto);

    /**
     * 审核拒绝
     * @param retreatCode  退住单code
     * @param reject   拒绝原因
     * @return
     */
    ResponseResult auditReject(String retreatCode, String reject, String taskId);

    /**
     * 撤回
     * @param retreatCode
     * @param flowStatus
     * @return
     */
    ResponseResult revocation(String retreatCode, Integer flowStatus, String taskId);

    /**
     * 撤销驳回
     * @param retreatCode
     * @return
     */
    ResponseResult disapprove(String retreatCode, String message, String taskId);

    /**
     * 退住管理列表查询
     * @return
     */
    ResponseResult selectByPage(RetreatReqDto retreatReqDto);

    /**
     * 撤销
     * @param retreatCode   退住编码
     * @return
     */
    ResponseResult cancel(String retreatCode, String taskId);

}
