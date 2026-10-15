package com.tzyl.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.tzyl.base.ResponseResult;
import com.tzyl.dto.RefundVoucherDto;
import com.tzyl.entity.Retreat;
import com.tzyl.entity.RetreatBill;
import com.tzyl.exception.BaseException;
import com.tzyl.mapper.RetreatBillMapper;
import com.tzyl.mapper.RetreatMapper;
import com.tzyl.service.RetreatBillService;
import com.tzyl.utils.UserThreadLocal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class RetreatBillServiceImpl implements RetreatBillService {

    @Autowired
    private RetreatBillMapper retreatBillMapper;

    @Autowired
    private RetreatMapper retreatMapper;

    /**
     * 上传退款凭证
     * @param refundVoucherDto
     * @return
     */
    @Override
    public ResponseResult uploadRefundVoucher(RefundVoucherDto refundVoucherDto) {

        Retreat retreat = retreatMapper.getRetreatByCode(refundVoucherDto.getRetreatCode());
        if(retreat == null){
            throw new BaseException("退住单不存在");
        }

        RetreatBill retreatBill = new RetreatBill();
        retreatBill.setRetreatId(retreat.getId());
        BeanUtil.copyProperties(refundVoucherDto,retreatBill);
        retreatBill.setElderId(retreat.getElderId());
        retreatBill.setCreateTime(LocalDateTime.now());
        retreatBill.setCreateBy(UserThreadLocal.getUserId());

        retreatBillMapper.update(retreatBill);

        return ResponseResult.success();
    }

    /**
     * 查询退住账单数据
     * @param retreatId
     * @return
     */
    @Override
    public ResponseResult getRetreatBill(Long retreatId) {
        RetreatBill retreatBill = retreatBillMapper.selectByRetreatId(retreatId);
        return ResponseResult.success(retreatBill);
    }
}
