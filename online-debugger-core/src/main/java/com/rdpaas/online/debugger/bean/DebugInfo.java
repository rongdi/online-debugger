package com.rdpaas.online.debugger.bean;

import java.util.ArrayList;
import java.util.List;

/**
 * 调试返回信息
 * 包含当前执行的类名，方法名，所在行号，是否结束，方法参数，变量（全局和局部）信息
 * @author rongdi
 * @date 2021/1/24
 */
public class DebugInfo {

    private Boolean end = false;

    private Integer lineNumber;

    private String className;

    private String methodName;

    private List<VarInfo> fields = new ArrayList<>();

    private List<VarInfo> args = new ArrayList<>();

    private List<VarInfo> vars = new ArrayList<>();

    public final static class VarInfo {

        private String name;

        private Object value;

        private String type;

        public VarInfo(String name, String type, Object value) {
            this.name = name;
            this.value = value;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return "VarInfo{" +
                    "name='" + name + '\'' +
                    ", value=" + value +
                    ", type='" + type + '\'' +
                    '}';
        }
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public List<VarInfo> getFields() {
        return fields;
    }

    public void setFields(List<VarInfo> fields) {
        this.fields = fields;
    }

    public List<VarInfo> getArgs() {
        return args;
    }

    public void setArgs(List<VarInfo> args) {
        this.args = args;
    }

    public List<VarInfo> getVars() {
        return vars;
    }

    public void setVars(List<VarInfo> vars) {
        this.vars = vars;
    }

    public Boolean getEnd() {
        return end;
    }

    public void setEnd(Boolean end) {
        this.end = end;
    }

    @Override
    public String toString() {
        return "DebugInfo{" +
                "end=" + end +
                ", lineNumber=" + lineNumber +
                ", className='" + className + '\'' +
                ", methodName='" + methodName + '\'' +
                ", args=" + args +
                ", vars=" + vars +
                '}';
    }
}
