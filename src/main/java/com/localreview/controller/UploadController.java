package com.localreview.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.localreview.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    /** 图片保存根目录，可通过 app.upload-dir 配置，默认 ./uploads/ 相对启动路径 */
    @Value("${app.upload-dir:./uploads/}")
    private String uploadDir;

    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            // 生成新文件名
            String fileName = createNewFileName(originalFilename);
            // 保存文件
            image.transferTo(new File(uploadDir, fileName));
            // 返回结果
            log.debug("文件上传成功，{}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        try {
            File baseDir = new File(uploadDir).getCanonicalFile();
            File file = new File(baseDir, filename).getCanonicalFile();
            // 防止通过 ../ 或绝对路径删除上传目录以外的文件
            if (!file.toPath().startsWith(baseDir.toPath()) || file.isDirectory()) {
                return Result.fail("错误的文件名称");
            }
            FileUtil.del(file);
            return Result.ok();
        } catch (IOException e) {
            return Result.fail("错误的文件名称");
        }
    }

    private String createNewFileName(String originalFilename) {
        if (StrUtil.isBlank(originalFilename)) {
            throw new IllegalArgumentException("文件名称不能为空");
        }
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        if (StrUtil.isBlank(suffix) || !suffix.matches("(?i)[a-z0-9]{1,10}")) {
            throw new IllegalArgumentException("文件格式错误");
        }
        // 生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 判断目录是否存在
        File dir = new File(uploadDir, StrUtil.format("/blogs/{}/{}", d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 生成文件名
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}
