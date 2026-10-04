package com.zzyl.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.zzyl.base.PageResponse;
import com.zzyl.constant.ReservationCacheConstant;
import com.zzyl.dto.ReservationDto;
import com.zzyl.dto.VisitDto;
import com.zzyl.entity.Reservation;
import com.zzyl.enums.ReservationStatus;
import com.zzyl.exception.BaseException;
import com.zzyl.mapper.ReservationMapper;
import com.zzyl.service.ElderService;
import com.zzyl.service.ReservationService;
import com.zzyl.service.VisitService;
import com.zzyl.utils.UserThreadLocal;
import com.zzyl.vo.ReservationVo;
import com.zzyl.vo.TimeCountVo;
import com.zzyl.vo.retreat.ElderVo;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class ReservationServiceImpl implements ReservationService {

    @Autowired
    private ReservationMapper reservationMapper;

    @Resource
    private VisitService visitService;

    @Resource
    private ElderService elderService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 锁内使用的独立事务模板(REQUIRES_NEW)：
     * 类级@Transactional会在方法返回后才提交，若直接在锁内写入，
     * 释放锁时名额占用尚未提交，并发请求仍可能读到旧计数导致超约，
     * 因此容量校验+写入必须在独立事务中、释放锁之前完成提交
     */
    private TransactionTemplate lockTxTemplate;

    @PostConstruct
    public void initLockTxTemplate() {
        lockTxTemplate = new TransactionTemplate(transactionManager);
        lockTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 添加预约
     */
    @Override
    public void add(ReservationDto dto) {
        if (dto.getTime() == null) {
            throw new BaseException("预约时间不能为空");
        }
        if (dto.getType().equals(1)) {
            ElderVo elderVo = elderService.selectByPrimaryKey(dto.getElderId());
            if (elderVo.getStatus() == 3) {
                throw new BaseException("退住中，不可预约");
            }
            if (elderVo.getStatus() == 5) {
                throw new BaseException("已退住，不可预约");
            }
        }
        Long userId = UserThreadLocal.getUserId();

        // 取消次数大于3次的也不能预约
        int cancelledReservationCount = getCancelledReservationCount(userId);

        // 如果已经预约的次数超过3次，则不需要再进行预约
        if (cancelledReservationCount >= 3) {
            throw new BaseException("今天取消次数已达上限，不可进行预约");
        }

        // 否则，允许添加预约
        Reservation reservation = new Reservation();
        //属性拷贝
        BeanUtils.copyProperties(dto, reservation);
        reservation.setStatus(ReservationStatus.PENDING.getOrdinal());
        reservation.setCreateBy(userId);

        // 对预约时段加分布式锁，同一时段的请求串行化，防止并发下超约
        RLock lock = redissonClient.getLock(ReservationCacheConstant.LOCK_PREFIX + LocalDateTimeUtil.formatNormal(dto.getTime()));
        boolean locked = false;
        try {
            locked = lock.tryLock(ReservationCacheConstant.LOCK_WAIT_TIME, ReservationCacheConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!locked) {
                throw new BaseException("当前预约人数较多，请稍后再试");
            }
            // 锁内独立事务完成"容量校验+插入"并提交，保证释放锁前名额占用对其他请求可见
            lockTxTemplate.executeWithoutResult(status -> {
                if (reservationMapper.countByTime(dto.getTime()) >= ReservationCacheConstant.MAX_PER_SLOT) {
                    throw new BaseException("该时段已约满，请选择其他时间");
                }
                try {
                    reservationMapper.insert(reservation);
                }
                catch (Exception e) {
                    log.info(e +"");
                    throw new BaseException("此手机号已预约该时间");
                }
            });
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException("预约失败，请稍后重试");
        }
        finally {
            if (locked) {
                lock.unlock();
            }
        }
    }

    /**
     * 更新预约
     */
    @Override
    public void update(Long id, ReservationDto dto) {
        Reservation reservation = reservationMapper.findById(id);
        if (reservation == null) {
            return;
        }
        // 时间未变化，或更新后为取消状态(不占名额)，无需容量校验
        boolean timeChanged = dto.getTime() != null && !dto.getTime().equals(reservation.getTime());
        boolean canceled = dto.getStatus() != null && dto.getStatus().equals(ReservationStatus.CANCELED.getOrdinal());
        if (!timeChanged || canceled) {
            doUpdate(reservation, dto, id);
            return;
        }
        // 时间变更：对目标时段加锁，防止并发下把预约改到已满时段
        RLock lock = redissonClient.getLock(ReservationCacheConstant.LOCK_PREFIX + LocalDateTimeUtil.formatNormal(dto.getTime()));
        boolean locked = false;
        try {
            locked = lock.tryLock(ReservationCacheConstant.LOCK_WAIT_TIME, ReservationCacheConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!locked) {
                throw new BaseException("当前预约人数较多，请稍后再试");
            }
            lockTxTemplate.executeWithoutResult(status -> {
                if (reservationMapper.countByTime(dto.getTime()) >= ReservationCacheConstant.MAX_PER_SLOT) {
                    throw new BaseException("该时段已约满，请选择其他时间");
                }
                doUpdate(reservation, dto, id);
            });
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException("更新预约失败，请稍后重试");
        }
        finally {
            if (locked) {
                lock.unlock();
            }
        }
    }

    /**
     * 执行预约更新
     */
    private void doUpdate(Reservation reservation, ReservationDto dto, Long id) {
        BeanUtils.copyProperties(dto, reservation);
        reservation.setId(id);
        reservation.setUpdateTime(LocalDateTime.now());
        reservationMapper.update(reservation);
    }

    /**
     * 取消预约
     */
    @Override
    public void cancelReservation(Long id) {
        Reservation reservation = reservationMapper.findById(id);
        if (reservation != null) {
            reservation.setStatus(ReservationStatus.CANCELED.getOrdinal());
            reservationMapper.update(reservation);
        }
    }

    /**
     * 根据id删除预约
     */
    @Override
    public void deleteById(Long id) {
        reservationMapper.deleteById(id);
    }

    /**
     * 根据id查找预约
     */
    @Override
    public ReservationVo findById(Long id) {
        Reservation reservation = reservationMapper.findById(id);
        if (reservation != null) {
            return convertToVO(reservation);
        }
        return null;
    }


    /**
     * 查找所有预约
     * @param mobile 预约人手机号
     * @param time 预约时间
     */
    @Override
    public List<ReservationVo> findAll(String mobile, LocalDateTime time) {
        LocalDateTime endTime = time.plusHours(24); // 计算24小时后的时间
        Long userId = UserThreadLocal.getUserId();
        List<Reservation> reservations = reservationMapper.findAll(userId, mobile, time, endTime); // 根据mobile和时间范围查询预约
        return convertToVOList(reservations);
    }



    /**
     * 分页查找预约
     * @param page 页码
     * @param size 每页大小
     * @param name 预约人姓名
     * @param phone 预约人手机号
     * @param status 预约状态
     * @param type 预约类型
     * @param startTime
     * @param endTime
     * @return 预约列表
     */
    @Override
    public PageResponse<ReservationVo> findByPage(int page, int size, String name, String phone, Integer status, Integer type, LocalDateTime startTime, LocalDateTime endTime) {
        PageHelper.startPage(page, size);
        Long userId = UserThreadLocal.getUserId();
        Page<Reservation> byPage = reservationMapper.findByPage(page, size, name, phone, status, type, userId, startTime, endTime);
        return PageResponse.of(byPage, ReservationVo.class);
    }


    /**
     * 将Reservation转换为ReservationVO
     */
    private ReservationVo convertToVO(Reservation reservation) {
        return BeanUtil.toBean(reservation, ReservationVo.class);
    }

    /**
     * 将List<Reservation>转换为List<ReservationVO>
     */
    private List<ReservationVo> convertToVOList(List<Reservation> reservations) {
        return reservations.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /**
     * 将时间戳转换为字符串
     */
    private String convertTimeToStr(Long time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(time));
    }

    /**
     * 如果预约未完成，则将预约状态更新为过期
     */
    @Override
    public void updateVisitReservationStatusToExpiredIfNotCompleted(Long id) {
        Reservation visitReservation = reservationMapper.findById(id);
        if (visitReservation.getStatus().equals(ReservationStatus.COMPLETED.getOrdinal())
                || visitReservation.getStatus().equals(ReservationStatus.CANCELED.getOrdinal()) ) {
            return;
        }
        LocalDateTime reservationTime = visitReservation.getTime();
        LocalDateTime currentTime = LocalDateTime.now();
        if (currentTime.isAfter(reservationTime.plusHours(1))) {
            visitReservation.setStatus(ReservationStatus.EXPIRED.getOrdinal());
            reservationMapper.update(visitReservation);
        }
    }

    /**
     * 查询每个时间段剩余预约次数
     * @param time 时间 日
     * @return 每个时间段剩余预约次数
     */
    @Override
    public List<TimeCountVo> countReservationsForEachTimeWithinTimeRange(LocalDateTime time) {
        LocalDateTime endTime = time.plusHours(24);
        return reservationMapper.countReservationsForEachTimeWithinTimeRange(time, endTime);
    }

    /**
     * 获取取消预约次数
     * @param updateBy 更新人id
     * @return 取消预约次数
     */
    @Override
    public int getCancelledReservationCount(Long updateBy) {
        return reservationMapper.countCancelledReservationsWithinTimeRange(LocalDateTime.now().withHour(0).withMinute(0).withSecond(0),
                LocalDateTime.now().withHour(23).withMinute(59).withSecond(59), updateBy);
    }

    /**
     * 来访
     * @param id ID
     * @param time 时间
     */
    @Override
    public void visit(Long id, Long time) {
        Reservation reservation = reservationMapper.findById(id);
        if (reservation != null) {
            reservation.setStatus(ReservationStatus.COMPLETED.getOrdinal());
            reservationMapper.update(reservation);
            VisitDto visitDto = BeanUtil.toBean(reservation, VisitDto.class);
            visitDto.setTime(LocalDateTimeUtil.of(time));
            visitService.add(visitDto);
        }
    }

    @Override
    public void updateReservationStatus(LocalDateTime now) {
        reservationMapper.updateReservationStatus(now);
    }
}

