package com.localreview.service;

import com.localreview.dto.Result;
import com.localreview.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @since 2021-12-22
 */
public interface IShopTypeService extends IService<ShopType> {

    Result queryTypeList();
}
