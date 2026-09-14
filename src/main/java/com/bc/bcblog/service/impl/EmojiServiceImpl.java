package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.SysEmoji;
import com.bc.bcblog.mapper.SysEmojiMapper;
import com.bc.bcblog.service.EmojiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 表情包服务实现。 */
@Service
@RequiredArgsConstructor
public class EmojiServiceImpl implements EmojiService {

    private final SysEmojiMapper emojiMapper;

    @Override
    public List<SysEmoji> list() {
        return emojiMapper.selectList(new LambdaQueryWrapper<SysEmoji>()
                .orderByAsc(SysEmoji::getPack)
                .orderByAsc(SysEmoji::getSortOrder)
                .orderByAsc(SysEmoji::getId));
    }

    @Override
    public List<SysEmoji> enabledList() {
        return emojiMapper.selectList(new LambdaQueryWrapper<SysEmoji>()
                .eq(SysEmoji::getEnabled, 1)
                .orderByAsc(SysEmoji::getPack)
                .orderByAsc(SysEmoji::getSortOrder)
                .orderByAsc(SysEmoji::getId));
    }

    @Override
    public void save(SysEmoji emoji) {
        if (emoji.getUrl() == null || emoji.getUrl().trim().isEmpty()) {
            throw new BusinessException("请先上传表情图片");
        }
        emoji.setUrl(emoji.getUrl().trim());
        if (emoji.getPack() != null) {
            emoji.setPack(emoji.getPack().trim());
        }
        if (emoji.getName() != null) {
            emoji.setName(emoji.getName().trim());
        }
        if (emoji.getSortOrder() == null) {
            emoji.setSortOrder(0);
        }
        if (emoji.getEnabled() == null) {
            emoji.setEnabled(1);
        }
        if (emoji.getId() == null) {
            emoji.setCreateTime(LocalDateTime.now());
            emojiMapper.insert(emoji);
        } else {
            emojiMapper.updateById(emoji);
        }
    }

    @Override
    public void delete(Long id) {
        emojiMapper.deleteById(id);
    }
}
