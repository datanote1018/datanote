package com.datanote.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datanote.mapper.DnPromptMapper;
import com.datanote.model.DnPrompt;
import com.datanote.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
public class PromptController {

    private final DnPromptMapper promptMapper;

    @GetMapping
    public R<List<DnPrompt>> list() {
        return R.ok(promptMapper.selectList(new QueryWrapper<DnPrompt>().orderByAsc("id")));
    }

    @GetMapping("/{id}")
    public R<DnPrompt> get(@PathVariable Long id) {
        return R.ok(promptMapper.selectById(id));
    }

    @PostMapping
    public R<DnPrompt> create(@RequestBody DnPrompt body) {
        if (body.getCode() == null || body.getCode().trim().isEmpty()) return R.fail("code 不能为空");
        if (body.getName() == null || body.getName().trim().isEmpty()) return R.fail("名称不能为空");
        if (body.getContent() == null || body.getContent().trim().isEmpty()) return R.fail("提示词内容不能为空");
        promptMapper.insert(body);
        return R.ok(body);
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody DnPrompt body) {
        body.setId(id);
        promptMapper.updateById(body);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        promptMapper.deleteById(id);
        return R.ok();
    }
}
