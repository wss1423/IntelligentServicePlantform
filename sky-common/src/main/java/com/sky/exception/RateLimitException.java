package com.sky.exception;

/**
 * 接口限流异常
 * 当某个接口在滑动窗口内的调用次数超过阈值时抛出，由全局异常处理器统一返回提示信息
 *
 * @Author: shun wang
 */
public class RateLimitException extends BaseException {

    public RateLimitException() {
    }

    public RateLimitException(String msg) {
        super(msg);
    }

}
