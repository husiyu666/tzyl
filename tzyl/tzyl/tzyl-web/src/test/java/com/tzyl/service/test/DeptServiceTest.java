package com.tzyl.service.test;

import com.tzyl.dto.DeptDto;
import com.tzyl.service.DeptService;
import com.tzyl.utils.StringUtils;
import com.tzyl.vo.DeptVo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DeptServiceTest {

    @Autowired
    private DeptService deptService;

    @Test
    public void testCreateDept() {
        DeptDto deptDto = DeptDto.builder()
                .deptName(StringUtils.getRandom(5))
                .dataState("0")
                .leaderId(1671403256519078030L)
                .parentDeptNo("100001000000000")
                .build();
        boolean result = deptService.createDept(deptDto);
        assertTrue(result);
    }

    @Test
    public void testUpdateDept() {
        DeptDto deptDto = DeptDto.builder()
                .deptName(StringUtils.getRandom(5))
                .dataState("0")
                .leaderId(1671403256519078030L)
                .parentDeptNo("100001000000000")
                .build();
        deptDto.setId(1671445634122588264L);
        boolean result = deptService.updateDept(deptDto);
        assertTrue(result);
    }

    @Test
    public void testFindDeptList() {
        DeptDto deptDto = new DeptDto();
        List<DeptVo> deptVoList = new ArrayList<>();
        deptVoList = deptService.findDeptList(deptDto);

        assertTrue(deptVoList.size() > 0);
        assertNotNull(deptVoList);

    }

    @Test
    public void testDeleteDeptById() {
        int result = deptService.deleteDeptById("1671445634122588271");
        assertEquals(1, result);
    }
}