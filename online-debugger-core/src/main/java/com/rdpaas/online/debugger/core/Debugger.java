package com.rdpaas.online.debugger.core;

import com.rdpaas.online.debugger._enum.EventType;
import com.rdpaas.online.debugger.bean.DebugInfo;
import com.sun.jdi.*;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;
import com.sun.tools.jdi.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 核心调试类
 * @author rongdi
 * @date 2021/1/24
 */
public class Debugger {

    /**
     * 用于存储用户调试标识和Debugger对象的映射关系
     */
    private static Map<String,Debugger> map = new ConcurrentHashMap<>();

    /**
     * 当前Debugger连接被调试程序后返回的虚拟机对象
     */
    private VirtualMachine virtualMachine;

    /**
     * 被调试程序执行的当前线程引用
     */
    private ThreadReference threadReference;

    /**
     * 当前Debugger正在处理的事件请求
     */
    private EventRequest eventRequest;

    /**
     * 当前Debugger正在处理的事件集
     */
    private EventSet eventsSet;

    /**
     * 用户调试标识，主要是用来隔离不同用户、不同调试请求的标识，如果同一个用户只能在一个地方登录
     * 那么可以直接用用户id作为标识
     */
    private String tag;

    private Debugger(String tag,String hostname,Integer port) throws Exception {
        this.tag = tag;
        virtualMachine = connJVM(hostname, port);
    }

    /**
     * 根据标识获取当前Debugger对象
     * @param tag 调试标识，为了区分不同用户的不同调试请求，如果同一个用户只能在一个地方登录可以直接使用
     *            用户id作为标识
     * @param hostname 调试连接的目标主机地址/IP
     * @param port 被调试程序为调试开发的调试端口 -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
     */
    public static Debugger getInstance(String tag,String hostname,Integer port) throws Exception {
        Debugger debugger = map.get(tag);
        if(debugger == null) {
            debugger = new Debugger(tag, hostname, port);
            map.put(tag, debugger);
        }
        return debugger;
    }

    /**
     * 根据标识获取当前使用的调试对象
     * @param tag
     * @return
     */
    public static Debugger getInstance(String tag) {
        return map.get(tag);
    }

    /**
     * 打断点并获取当前执行的类，方法，各种变量信息，主要是给调试端断点调试的场景，
     * 当前执行之后有断点，使用此方法会直接运行到断点处，需要注意的是不要两次请求打同一行的断点，这样会导致第二次断点
     * 执行时如果后续没有断点了，会直接执行到连接断开
     * @param className
     * @param lineNumber
     * @return
     * @throws Exception
     */
    public DebugInfo markBpAndGetInfo(String className, Integer lineNumber) throws Exception {
        markBreakpoint(className, lineNumber);
        return getInfo();
    }

    /**
     * 单步调试，
     * STEP_INTO(1) 执行到方法里
     * STEP_OVER(2) 执行下一行代码
     * STEP_OUT(3)  跳出方法执行
     * @param eventType
     * @return
     * @throws Exception
     */
    public DebugInfo stepAndGetInfo(EventType eventType) throws Exception {
        createEvent(eventType);
        return getInfo();
    }

    /**
     * 当断点到最后一行后，调用断开连接结束调试
     */
    public DebugInfo disconnect() throws Exception {
        virtualMachine.dispose();
        map.remove(tag);
        return getInfo();
    }

    /**
     * 连接指定主机的指定调试端口返回一个虚拟主机对象，以下属于公式代码就不做解释了
     * @param hostname 待调试程序的主机地址
     * @param port 调试程序开放的后门调试端口
     * @return
     * @throws Exception
     */
    private VirtualMachine connJVM(String hostname, Integer port) throws Exception {

        VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
        List<AttachingConnector> connectors = vmm.attachingConnectors();
        SocketAttachingConnector sac = null;
        for(AttachingConnector ac:connectors) {
            if(ac instanceof SocketAttachingConnector) {
                sac = (SocketAttachingConnector) ac;
            }
        }
        if(sac == null) {
            throw new Exception("未找到SocketAttachingConnector连接器");
        }
        Map<String, Connector.Argument> arguments = sac.defaultArguments();
        arguments.get("hostname").setValue(hostname);
        arguments.get("port").setValue(String.valueOf(port));
        return sac.attach(arguments);
    }

    /**
     * 在指定类的指定行打上断点
     * @param className 类的全限定名
     * @param line 断点所在的有效行号（不要不讲武德打在空白行上）
     * @throws Exception
     */
    private void markBreakpoint(String className, Integer line) throws Exception {
        /**
         * 根据虚拟主机拿到一个事件请求管理器
         */
        EventRequestManager eventRequestManager = virtualMachine.eventRequestManager();
        /**
         * 主要是为了添加当前断点是把之前断点事删掉,
         */
        if(eventRequest != null) {
            eventRequestManager.deleteEventRequest(eventRequest);
        }
        /**
         * 根据调试类的全限定名，拿到一个调试类的远程引用类型，请注意这里是远程调试，在当前调试程序的jvm中不会
         * 装载有被调试类，所以这里只能是得到一个包装后的类型，至于为啥是个集合，是因为这个被调试类可能正在被多个
         * 线程调用
         */
        List<ReferenceType> rts = virtualMachine.classesByName(className);
        if(rts == null || rts.isEmpty()) {
            throw new Exception("无法获取有效的debug类");
        }

        /**
         * 不要说我不讲武德，正常的本地调试在多线程环境中也只能调试最先到达的那个线程的调用，所以这里也是直接
         * 获取第一个线程调用，同样只能可怜兮兮的获取到一个Class的包装类型，谁叫我们是远程调试呢
         */
        ClassType classType = (ClassType) rts.get(0);
        /**
         * 根据行获取位置对象，这里为啥又是个集合，好吧我承认忽悠不过去了，我也不明白，谁叫这JDI是人家设计的呢
         */
        List<Location> locations = classType.locationsOfLine(line);
        if(locations == null || locations.isEmpty()) {
            throw new Exception("无法获取有效的debug行");
        }
        /**
         * 一如既往的获取第一个位置信息
         */
        Location location = locations.get(0);

        /**
         * 创建一个端点并激活，这是公式代码，下面的EventRequest.SUSPEND_EVENT_THREAD表示端点执行过程阻塞当前线程，
         * SUSPEND_ALL 表示阻塞所有线程。实际上创建并激活的事件请求会被放在一个时间队列中
         */
        BreakpointRequest breakpointRequest = eventRequestManager.createBreakpointRequest(location);
        breakpointRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        breakpointRequest.enable();

        /**
         * 当前端点创建好了，赶紧释放被调试程序，让他有机会执行到当前端点，如果不放行就会一直卡在当前端点之前的其它端点，
         * 没机会到这里了，这里选择在这里放行而不是在执行完上一个端点后马上放行是因为我们的端点调试的断点请求并不是
         * 刚开始调试就确定好的，而是执行到当前行后由前端判断本行是否有断点，然后请求到调试程序的，属于动态添加断点，
         * 如果上一个端点执行完，马上释放那么当前端点可能都还没请求就过去了。
         */
        if(eventsSet != null) {
            eventsSet.resume();
        }

    }

    /**
     * 众所周知，debug单步调试过程最重要的几个调试方式：执行下一条（step_over），执行方法里面（step_into）,
     * 跳出方法(step_out)。
     * @param eventType 断点调试事件类型 STEP_INTO(1),STEP_OVER(2),STEP_OUT(3)
     * @return
     * @throws Exception
     */
    private EventRequest createEvent(EventType eventType) throws Exception {

        /**
         * 根据事件类型获取对应的事件请求对象并激活，最终会被放到事件队列中
         */
        EventRequestManager eventRequestManager = virtualMachine.eventRequestManager();

        /**
         * 主要是为了把当前事件请求删掉，要不然执行到下一行
         * 又要发送一个单步调试的事件，就会报一个线程只能有一种单步调试事件，这里很多细节都是
         * 本人花费大量事件调试得到的，可能不是最优雅的，但是肯定是可实现的
         */
        if(eventRequest != null) {
            eventRequestManager.deleteEventRequest(eventRequest);
        }

        eventRequest = eventRequestManager.createStepRequest(threadReference,StepRequest.STEP_LINE,eventType.getIndex());
        eventRequest.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        eventRequest.enable();

        /**
         * 同上创建断点事件，这里也是创建完事件，就释放被调试程序
         */
        if(eventsSet != null) {
            eventsSet.resume();
        }
        return eventRequest;
    }

    /**
     * 消费调试的事件请求，然后拿到当前执行的方法，参数，变量等信息，也就是debug过程中我们关注的那一堆变量信息
     * @return
     * @throws Exception
     */
    private DebugInfo getInfo() throws Exception {
        DebugInfo debugInfo = new DebugInfo();
        EventQueue eventQueue = virtualMachine.eventQueue();
        /**
         * 这个是阻塞方法，当有事件发出这里才可以remove拿到EventsSet
         */
        eventsSet= eventQueue.remove();
        EventIterator eventIterator = eventsSet.eventIterator();
        if(eventIterator.hasNext()) {
            Event event = eventIterator.next();
            /**
             * 一个debug程序能够debug肯定要有个断点，直接从断点事件这里拿到当前被调试程序当前的执行线程引用，
             * 这个引用是后面可以拿到信息的关键，所以保存在成员变量中，归属于当前的调试对象
             */
            if(event instanceof BreakpointEvent) {
                threadReference = ((BreakpointEvent) event).thread();
            } else if(event instanceof VMDisconnectEvent) {
                /**
                 * 这种事件是属于讲武德的判断方式，断点到最后一行之后调用virtualMachine.dispose()结束调试连接
                 */
                debugInfo.setEnd(true);
                return debugInfo;
            } else if(event instanceof StepEvent) {
                threadReference = ((StepEvent) event).thread();
            }
            try {
                /**
                 * 获取被调试类当前执行的栈帧，然后获取当前执行的位置
                 */
                StackFrame stackFrame = threadReference.frame(0);
                Location location = stackFrame.location();
                /**
                 * 当前走到线程退出了，就over了，这里其实是我在调试过程中发现如果调试的时候不讲武德，明明到了最后一行
                 * 还要发送一个STEP_OVER事件出来，就会报错。本着调试端就是客户，客户就是上帝的心态，做了一个不太优雅
                 * 的判断
                 */
                if("java.lang.Thread.exit()".equals(location.method().toString())) {
                    debugInfo.setEnd(true);
                    return debugInfo;
                }
                /**
                 * 无脑的封装返回对象
                 */
                debugInfo.setClassName(location.declaringType().name());
                debugInfo.setMethodName(location.method().name());
                debugInfo.setLineNumber(location.lineNumber());
                /**
                 * 封装成员变量
                 */
                ObjectReference or = stackFrame.thisObject();
                if(or != null) {
                    List<Field> fields = ((LocationImpl) location).declaringType().fields();
                    for(int i = 0;fields != null && i < fields.size();i++) {
                        Field field = fields.get(i);
                        Object val = parseValue(or.getValue(field),0);
                        DebugInfo.VarInfo varInfo = new DebugInfo.VarInfo(field.name(),field.typeName(),val);
                        debugInfo.getFields().add(varInfo);
                    }
                }
                /**
                 * 封装局部变量和参数，参数是方法传入的参数
                 */
                List<LocalVariable> varList = stackFrame.visibleVariables();
                for (LocalVariable localVariable : varList) {
                    /**
                     * 这地方使用threadReference.frame(0)而不是使用上面已经拿到的stackFrame，从代码上看是等价，
                     * 但是有个很坑的地方，如果使用stackFrame由于下面使用threadReference执行过invokeMethod会导致
                     * stackFrame的isValid为false，再次通过stackFrame.getValue就会报错，每次重新threadReference.frame(0)
                     * 就没有问题，由于看不到源码，个人推测threadReference.frame(0)这里会生成一份拷贝stackFrame，由于手动执行方法，
                     * 方法需要用到栈帧会导致执行完方法，这个拷贝的栈帧被销毁而变得不可用，而每次重新获取最上面得栈帧，就不会有问题
                     */
                    DebugInfo.VarInfo varInfo = new DebugInfo.VarInfo(localVariable.name(),localVariable.typeName(),parseValue(threadReference.frame(0).getValue(localVariable),0));
                    if(localVariable.isArgument()) {
                        debugInfo.getArgs().add(varInfo);
                    } else {
                        debugInfo.getVars().add(varInfo);
                    }
                }
            } catch(AbsentInformationException | VMDisconnectedException e1) {
                debugInfo.setEnd(true);
                return debugInfo;
            } catch(Exception e) {
                debugInfo.setEnd(true);
                return debugInfo;
            }

        }

        return debugInfo;
    }

    /**
     * 费劲的转换，一切都是因为调试类和被调试类不在一个JVM中，所以拿到的对象都只是一个包装类，拿不到源对象
     * @param value 待解析的值
     * @param depth 当前深度编号
     * @return
     * @throws Exception
     */
    private Object parseValue(Value value,int depth) throws Exception {
        if(value instanceof StringReference || value instanceof IntegerValue || value instanceof BooleanValue
                || value instanceof ByteValue || value instanceof CharValue || value instanceof ShortValue
                || value instanceof LongValue || value instanceof FloatValue || value instanceof DoubleValue) {
            return parseCommonValue(value);
        } else if(value instanceof ObjectReference) {
            int localDepth = depth;
            ObjectReference obj = (ObjectReference) value;
            String type = obj.referenceType().name();
            if("java.lang.Integer".equals(type) || "java.lang.Boolean".equals(type) || "java.lang.Float".equals(type)
                    || "java.lang.Double".equals(type) || "java.lang.Long".equals(type) || "java.lang.Byte".equals(type)
                    || "java.lang.Character".equals(type)) {
                Field f = obj.referenceType().fieldByName("value");
                return parseCommonValue(obj.getValue(f));
            } else if("java.util.Date".equals(type)) {
                Field field = obj.referenceType().fieldByName("fastTime");
                Date date = new Date(Long.parseLong("" + obj.getValue(field)));
                return date;
            } else if(value instanceof ArrayReference) {
                ArrayReference ar = (ArrayReference) value;
                List<Value> values = ar.getValues();
                List<Object> list = new ArrayList<>();
                for(int i = 0;i < values.size();i++) {
                    list.add(parseValue(values.get(i),depth));
                }
                return list;
                /**
                 * 个人感觉都已经有点不讲武德了，实在没有找到更优雅的方法了
                 */
            } else if(isCollection(obj)) {
                Method toArrayMethod = obj.referenceType().methodsByName("toArray").get(0);
                value = obj.invokeMethod(threadReference, toArrayMethod, Collections.emptyList(), 0);
                return parseValue(value,++localDepth);
            }  else if(isMap(obj)) {
                /**
                 * 这里是一个比较巧妙的利用递归方式，将map先转成集合，然后再调用本方法转成数组，然后就可以走到ArrayReference进行处理
                 */
                Method entrySetMethod = obj.referenceType().methodsByName("entrySet").get(0);
                value = obj.invokeMethod(threadReference, entrySetMethod, Collections.emptyList(), 0);
                return parseValue(value,++localDepth);
            } else {
                Map<String,Object> map = new HashMap<>();
                String className = obj.referenceType().name();
                map.put("class",className);
                /**
                 * 到了Object就不继续了
                 */
                if("java.lang.Object".equals(className)) {
                    return map;
                }
                List<Field> fields = obj.referenceType().allFields();
                for(int i = 0;fields != null && i < fields.size();i++) {
                    localDepth = depth;
                    /**
                     * 这里有个递归，万一被调试类不讲武德搞一个无限自循环的对象，比如Person类里有个成员变量p直接声明的时候
                     * 就new一个Person，这样这个Person对象的深度是无限的，为了防止内存溢出，限制深度不超过2，你要是不信邪，
                     * 你改成5试试，就本例的例子，执行到最后一行后，继续stepOver，可以给你返回上十万行数据，呵呵
                     */
                    if(localDepth < 2) {
                        Field f = fields.get(i);
                        map.put(f.name(), parseValue(obj.getValue(f), ++localDepth));
                    }
                }
                return map;
            }
        }
        return null;
    }

    /**
     * 万恶的穷举，真的是很恶心，如果不转直接放这个包装的Value出去变成json后就拿不到真实的value值,
     * 别看打印的时候可以打印,还好这些鬼东西是有规律的，调试的时候试出来了一个，其余都出来了
     * @param value
     * @return
     */
    private Object parseCommonValue(Value value) {
        if(value instanceof StringReference) {
            return ((StringReferenceImpl) value).value();
        } else if(value instanceof IntegerValue) {
            return ((IntegerValueImpl) value).value();
        } else if(value instanceof BooleanValue) {
            return ((BooleanValueImpl) value).value();
        } else if(value instanceof ByteValue) {
            return ((ByteValueImpl) value).value();
        } else if(value instanceof CharValue) {
            return ((CharValueImpl) value).value();
        } else if(value instanceof ShortValue) {
            return ((ShortValueImpl) value).value();
        } else if(value instanceof LongValue) {
            return ((LongValueImpl) value).value();
        } else if(value instanceof FloatValue) {
            return ((FloatValueImpl) value).value();
        } else if(value instanceof DoubleValue) {
            return ((DoubleValueImpl) value).value();
        } else {
            return null;
        }
    }

    /**
     * 判断是不是集合，经过了多轮的纠结，最开始尝试使用java.util开头，包含List的，如:
     * type.startsWith("java.util.") && ((type.indexOf("List") != -1) || (type.indexOf("Set") != -1))
     * 结果发现太片面，不讲武德都没法形容了，如果是List的实现类就没办法了，只能通过这种方式了，毕竟找了很多api找不到直接判断
     * 这个调试的镜像对象是否是集合的方法。请不要作死，明明不是集合，非要给自己的类定义一个toArray方法
     */
    private boolean isCollection(ObjectReference obj) throws ClassNotLoadedException {
        List<Method> toArrayMethods = obj.referenceType().methodsByName("toArray");
        boolean flag = false;
        for(int i = 0;i < toArrayMethods.size();i++) {
            Method toArrayMethod = toArrayMethods.get(i);
            flag = (toArrayMethod.argumentTypes().size() == 0);
            if(flag) {
                break;
            }
        }
        return flag;
    }

    /**
     * 判断是不是Map，经过了多轮的纠结，最开始尝试使用java.util开头，包含Map的，如：
     * (type.startsWith("java.util.") && (type.indexOf("Map") != -1) && !type.endsWith("$Node"))
     * 还是发现太片面，如果是Map的实现类就没办法了，只能通过这种判断是否有不带桉树的entrySet方法的方式了，你自己实现
     * 的类总不会明明不是一个map，你非要定义一个entrySet方法，这种作死的情况，我就不管了，毕竟找了很多api找不到
     * 直接判断这个调试的镜像对象是否是map的方法。
     */
    private boolean isMap(ObjectReference obj) throws ClassNotLoadedException {
        List<Method> toArrayMethods = obj.referenceType().methodsByName("entrySet");
        boolean flag = false;
        for(int i = 0;i < toArrayMethods.size();i++) {
            Method toArrayMethod = toArrayMethods.get(i);
            flag = (toArrayMethod.argumentTypes().size() == 0);
            if(flag) {
                break;
            }
        }
        return flag;
    }
}
