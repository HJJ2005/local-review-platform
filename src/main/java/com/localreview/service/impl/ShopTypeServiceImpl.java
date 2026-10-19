package com.localreview.service.impl;

import cn.hutool.json.JSONUtil;
import com.localreview.dto.Result;
import com.localreview.entity.ShopType;
import com.localreview.mapper.ShopTypeMapper;
import com.localreview.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
@Resource
private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        String shoptypeJson = stringRedisTemplate.opsForValue().get("cache:shop:type");
        if (shoptypeJson != null){
            List<ShopType> typeList = JSONUtil.toList(shoptypeJson, ShopType.class);
            return Result.ok(typeList);
        }
        List<ShopType> typeList = query().orderByAsc("sort").list();
        if (typeList == null || typeList.isEmpty()) {
            return Result.fail("店铺类型不存在");
        }
        stringRedisTemplate.opsForValue().set("cache:shop:type", JSONUtil.toJsonStr(typeList), 30L, TimeUnit.MINUTES);
        return Result.ok(typeList);
    }
}
