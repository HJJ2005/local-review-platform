package com.localreview.controller;


import com.localreview.dto.Result;
import com.localreview.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;
@PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId,@PathVariable("isFollow") Boolean isFollow){
    return followService.follow(followUserId,isFollow);
}
@GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId){
    return followService.isfollow(followUserId);
}
@GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id){
    return followService.followCommons(id);
}
}
