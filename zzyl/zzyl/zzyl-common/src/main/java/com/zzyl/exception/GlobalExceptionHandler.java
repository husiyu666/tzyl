package com.zzyl.exception;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.FileNotFoundException;
import java.nio.file.AccessDeniedException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 是否把异常详情(堆栈/SQL/服务器路径)返回给前端。
     * 默认 false：详情只写日志，不进 HTTP 响应体。
     */
    @Value("${zzyl.exception.detail-message-enabled:false}")
    private boolean detailMessageEnabled;

    /**
     * 处理自定义异常BaseException。
     * 返回自定义异常中的错误代码和错误消息。
     *
     * @param exception 自定义异常
     * @return 响应数据，包含错误代码和错误消息
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<Object> handleBaseException(BaseException exception) {
        exception.printStackTrace();
        if (ObjectUtil.isNotEmpty(exception.getCause())) {
            log.error("自定义异常处理 -> ", exception);
        }
        return ResponseEntity.ok(MapUtil.<String, Object>builder()
                .put("code", exception.getCode())
                .put("msg", exception.getDefaultMessage())
                .build());
    }

    /**
     * 处理文件上传超过最大限制异常。
     * 返回HTTP响应状态码500，包含错误代码和错误消息。
     *
     * @param exception 文件上传异常
     * @return 响应数据，包含错误代码和错误消息
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException exception) {
        exception.printStackTrace();
        if (ObjectUtil.isNotEmpty(exception.getCause())) {
            log.error("文件上传超过最大限制异常 -> ", exception);
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(MapUtil.<String, Object>builder()
                        .put("code", HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .put("msg", "上传图片大小不能超过5M，格式需为jpg、png、gif")
                        .build());
    }

    /**
     * 处理其他未知异常。
     * 返回HTTP响应状态码500，包含错误代码和异常堆栈信息。
     *
     * @param exception 未知异常
     * @return 响应数据，包含错误代码和异常堆栈信息
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnknownException(Exception exception) {
        // 堆栈必须完整落日志：这里无论有没有 cause 都要记，
        // 否则无 cause 的异常只会 printStackTrace 到 stderr，日志文件里查不到
        log.error("其他未知异常 -> ", exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(MapUtil.<String, Object>builder()
                        .put("code", HttpStatus.INTERNAL_SERVER_ERROR.value())
                        // 堆栈里含 SQL 语句、表名列名、Java 包结构和服务器路径，
                        // 默认只返回通用文案，排查问题看后端日志。
                        // 本地调试需要看详情时把 zzyl.exception.detail-message-enabled 设为 true
                        .put("msg", detailMessage(ExceptionUtil.stacktraceToString(exception),
                                "服务器内部错误，请联系管理员或查看后端日志"))
                        .build());
    }

    /**
     * 是否把异常详情返回给前端。
     * 默认关闭，避免把堆栈/SQL/服务器路径暴露给浏览器。
     */
    private String detailMessage(String detail, String fallback) {
        return detailMessageEnabled ? detail : fallback;
    }

    /**
     * 处理FileNotFoundException异常。
     * 返回HTTP响应状态码400，包含错误代码和错误消息。
     *
     * @param exception 文件未找到异常
     * @return 响应数据，包含错误代码和错误消息
     */
    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<Object> handleFileNotFoundException(FileNotFoundException exception) {
        log.error("文件不存在 -> ", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(MapUtil.<String, Object>builder()
                        .put("code", HttpStatus.BAD_REQUEST.value())
                        // getMessage() 里带服务器绝对路径，同样不能默认外泄
                        .put("msg", detailMessage(exception.getMessage(), "文件不存在"))
                        .build());
    }

    /**
     * 处理没有权限访问接口异常。
     * 返回HTTP响应状态码401，包含错误代码和错误消息。
     *
     * @param exception 权限访问异常
     * @return 响应数据，包含错误代码和错误消息
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException exception) {
        exception.printStackTrace();
        if (ObjectUtil.isNotEmpty(exception.getCause())) {
            log.error("没有权限访问接口异常 -> ", exception);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(MapUtil.<String, Object>builder()
                        .put("code", HttpStatus.UNAUTHORIZED.value())
                        .put("msg", "没有权限访问接口")
                        .build());
    }

    /**
     * 处理运行时异常。
     * 返回HTTP响应状态码500，包含错误代码和错误消息。
     *
     * @param exception 运行时异常
     * @return 响应数据，包含错误代码和错误消息
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(RuntimeException exception) {
        // 全局兜底的完整堆栈只落日志
        log.error("其他未知异常 -> ", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(MapUtil.<String, Object>builder()
                        .put("code", HttpStatus.INTERNAL_SERVER_ERROR.value())
                        // 本项目大量业务异常直接抛 RuntimeException("角色名称已存在" 等)，
                        // 消息本身就是给用户看的，不能屏蔽；SQL 等敏感信息在 cause 里，已进日志。
                        .put("msg", exception.getMessage())
                        .build());
    }

    /**
     * 处理key重复异常。
     * 返回HTTP响应状态码200，包含错误代码和错误消息。
     *
     * @param exception key重复异常
     * @return 响应数据，包含错误代码和错误消息
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Object> handleDuplicateKeyException(DuplicateKeyException exception) {
        exception.printStackTrace();
        if (ObjectUtil.isNotEmpty(exception.getCause())) {
            log.error("其他未知异常 -> ", exception);
        }
        return ResponseEntity.status(HttpStatus.OK)
                .body(MapUtil.<String, Object>builder()
                        .put("code", HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .put("msg", "操作失败，数据重复")
                        .build());
    }
}
