package com.rdpaas.online.debugger.controller;

import com.rdpaas.online.debugger.bean.DebugInfo;
import com.rdpaas.online.debugger.core.Debugger;
import com.rdpaas.online.debugger._enum.EventType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 调试接口
 * @author rongdi
 * @date 2021/1/24
 */
@RestController
public class DebuggerController {

    @RequestMapping("/breakpoint")
    public DebugInfo breakpoint(@RequestParam String tag, @RequestParam String hostname, @RequestParam Integer port, @RequestParam String className, @RequestParam Integer lineNumber) throws Exception {
        Debugger debugger = Debugger.getInstance(tag,hostname,port);
        return debugger.markBpAndGetInfo(className,lineNumber);
    }

    @RequestMapping("/stepInto")
    public DebugInfo stepInto(@RequestParam String tag) throws Exception {
        Debugger debugger = Debugger.getInstance(tag);
        return debugger.stepAndGetInfo(EventType.STEP_INTO);
    }

    @RequestMapping("/stepOver")
    public DebugInfo stepOver(@RequestParam String tag) throws Exception {
        Debugger debugger = Debugger.getInstance(tag);
        return debugger.stepAndGetInfo(EventType.STEP_OVER);
    }

    @RequestMapping("/stepOut")
    public DebugInfo step(@RequestParam String tag) throws Exception {
        Debugger debugger = Debugger.getInstance(tag);
        return debugger.stepAndGetInfo(EventType.STEP_OUT);
    }

    @RequestMapping("/disconnect")
    public DebugInfo disconnect(@RequestParam String tag) throws Exception {
        Debugger debugger = Debugger.getInstance(tag);
        return debugger.disconnect();
    }
}
