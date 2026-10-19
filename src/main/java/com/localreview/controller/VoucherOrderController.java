package com.localreview.controller;

import com.localreview.dto.Result;
import com.localreview.service.IVoucherOrderService;    // ← ① 加上这个 import
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;   // ← ② 类型改成 IVoucherOrderService，变量名建议小写开头

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {

        return voucherOrderService.seckillVoucher(voucherId);
    }
}
