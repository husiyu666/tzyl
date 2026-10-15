package com.tzyl.vo;

import com.tzyl.entity.Applications;
import lombok.Data;

/**
 * @author husiyu
 */
@Data
public class ApplicationsVo extends Applications {

    /**
     * 单据流程状态
     */
    private Integer flowStatus;
}
