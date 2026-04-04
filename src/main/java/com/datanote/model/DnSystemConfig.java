package com.datanote.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("dn_system_config")
public class DnSystemConfig {
    @TableId(value = "config_key", type = IdType.INPUT)
    private String configKey;
    private String configValue;
    private String description;
    private LocalDateTime updatedAt;
}
