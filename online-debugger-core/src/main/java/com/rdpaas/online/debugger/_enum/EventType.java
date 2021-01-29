package com.rdpaas.online.debugger._enum;

/**
 * 调试事件类型
 * @author rongdi
 * @date 2021/1/24
 */
public enum EventType {
    // 进入方法
    STEP_INTO(1),
    // 下一条
    STEP_OVER(2),
    // 跳出方法
    STEP_OUT(3);

    private int index;

    private EventType(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public static EventType getType(Integer type) {
        if(type == null) {
            return STEP_OVER;
        }
        if(type.equals(1)) {
            return STEP_INTO;
        } else if(type.equals(3)){
            return STEP_OUT;
        } else {
            return STEP_OVER;
        }
    }
}

