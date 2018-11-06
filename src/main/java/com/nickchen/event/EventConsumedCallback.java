package com.nickchen.event;

/**
 * 事件执行完成回调
 *
 * @author nickChen
 */
public interface EventConsumedCallback {

    /**
     * 事件完成后回调的方法
     */
    void callback();
}
