
package com.tzyl.vo;

import com.tzyl.base.BaseVo;
import com.tzyl.vo.retreat.ElderVo;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 客户老人关联实体类
 */
@Data
@ApiModel(value = "MemberElderVo对象", description = "客户老人关联实体类")
public class MemberElderVo extends BaseVo {

    /**
     * 客户id
     */
    @ApiModelProperty(value = "客户id")
    private Long memberId;

    /**
     * 老人id
     */
    @ApiModelProperty(value = "老人id")
    private Long elderId;

    @ApiModelProperty(value = "老人")
    private ElderVo elderVo;

    @ApiModelProperty(value = "床位")
    private com.tzyl.vo.BedVo bedVo;

    @ApiModelProperty(value = "房间")
    private RoomVo roomVo;

    @ApiModelProperty(value = "绑定的智能设备id列表")
    private List<DeviceVo> deviceVos;
}


