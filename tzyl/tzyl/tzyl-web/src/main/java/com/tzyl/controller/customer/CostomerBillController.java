package com.tzyl.controller.customer;

import com.tzyl.base.PageResponse;
import com.tzyl.base.ResponseResult;
import com.tzyl.dto.BillDto;
import com.tzyl.service.BillService;
import com.tzyl.vo.BillVo;
import com.tzyl.vo.TradingVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * Bill控制器
 */
@Api(tags = "客户账单")
@RestController
@RequestMapping("/customer/bill")
public class CostomerBillController {

    @Autowired
    private BillService billService;


    /**
     * 支付账单
     */
    @ApiOperation("支付账单")
    @PutMapping
    public ResponseResult<TradingVo> pay(@RequestBody BillDto billDto) {
        TradingVo pay = billService.pay(billDto);
        return ResponseResult.success(pay);
    }

    /**
     * 根据id查询账单
     */
    @ApiOperation("根据id查询账单")
    @GetMapping("/{id}")
    public ResponseResult<BillVo> getById(@PathVariable Long id) {
        BillVo billVo = billService.selectByPrimaryKey(id);
        return ResponseResult.success(billVo);
    }

    /**
     * 分页查询账单
     */
    @ApiOperation("分页查询账单")
    @GetMapping("/page/")
    public ResponseResult<PageResponse<BillVo>> getBillPage(
                                                        @ApiParam(value = "支付状态") @RequestParam(name = "transactionStatus", required = false)  Integer transactionStatus,
                                                        @ApiParam(value = "老人Id") @RequestParam(name = "elderId", required = false) Long elderId,
                                                        @ApiParam(value = "页码（默认为1）") @RequestParam(name = "pageNum", defaultValue = "1") Integer pageNum,
                                                        @ApiParam(value = "每页数量（默认为10）") @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize) {
        PageResponse<BillVo> billPage = billService.getBillPage(null, null, null, null, null, transactionStatus, elderId, pageNum, pageSize);
        return ResponseResult.success(billPage);
    }
}
