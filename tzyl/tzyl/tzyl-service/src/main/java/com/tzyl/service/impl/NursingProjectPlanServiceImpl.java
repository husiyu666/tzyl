package com.tzyl.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.tzyl.dto.NursingProjectPlanDto;
import com.tzyl.entity.NursingProjectPlan;
import com.tzyl.mapper.NursingProjectPlanMapper;
import com.tzyl.service.NursingProjectPlanService;
import com.tzyl.vo.NursingProjectPlanVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NursingProjectPlanServiceImpl implements NursingProjectPlanService {

    @Autowired
    private NursingProjectPlanMapper nursingProjectPlanMapper;

    @Autowired
    public NursingProjectPlanServiceImpl(NursingProjectPlanMapper nursingProjectPlanMapper) {
        this.nursingProjectPlanMapper = nursingProjectPlanMapper;
    }


    @Override
    public int add(NursingProjectPlanDto nursingProjectPlan) {
        NursingProjectPlan projectPlan = BeanUtil.toBean(nursingProjectPlan, NursingProjectPlan.class);
        return nursingProjectPlanMapper.insert(projectPlan);
    }

    @Override
    public int update(NursingProjectPlanDto nursingProjectPlan) {
        NursingProjectPlan projectPlan = BeanUtil.toBean(nursingProjectPlan, NursingProjectPlan.class);
        return nursingProjectPlanMapper.update(projectPlan);
    }

    @Override
    public int deleteByIds(List<Long> ids) {
        return nursingProjectPlanMapper.deleteByIds(ids);
    }

    @Override
    public NursingProjectPlanVo getById(Long id) {
        NursingProjectPlan byId = nursingProjectPlanMapper.getById(id);
        return BeanUtil.toBean(byId, NursingProjectPlanVo.class);
    }

    @Override
    public int addList(List<NursingProjectPlanDto> projectPlans) {
        List<NursingProjectPlan> projectPlanList = projectPlans.stream().map(v -> BeanUtil.toBean(v, NursingProjectPlan.class)).collect(Collectors.toList());
        return nursingProjectPlanMapper.insertList(projectPlanList);
    }
}


